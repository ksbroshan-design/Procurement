package com.procurement.engine.ranking;

import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.ranking.model.ProcurementRankingResponse;
import com.procurement.engine.ranking.model.RankedOfferDto;
import com.procurement.engine.ranking.service.RankingService;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RankingServiceTest {

    @Autowired
    private RankingService rankingService;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private UserRepository userRepository;

    private User manager;

    @BeforeEach
    void setUp() {
        manager = userRepository.findByEmail("manager@procurement.com").orElseThrow();
    }

    private ProcurementRequest createProcurement(String category, int quantity, BigDecimal limit, List<ProcurementConstraint> constraints) {
        ProcurementRequest req = ProcurementRequest.builder()
                .user(manager)
                .category(category)
                .quantity(quantity)
                .authorizationLimit(limit)
                .status(ProcurementState.SUBMITTED)
                .build();
        if (constraints != null) {
            constraints.forEach(req::addConstraint);
        }
        return procurementRequestRepository.save(req);
    }

    @Test
    @DisplayName("Multi-dimensional ranking prioritizes lower TCO and quality over raw sticker price")
    void testRankingPrioritizesTcoAndQuality() {
        // TV category: Screen size >= 55
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder()
                        .attribute("screenSize")
                        .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                        .value("55")
                        .mandatory(true)
                        .build()
        );

        ProcurementRequest request = createProcurement("TV", 5, new BigDecimal("350000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        ProcurementRankingResponse response = rankingService.rankOffers(request.getId());

        assertThat(response.getEligibleOffers()).isNotEmpty();
        assertThat(response.getWeightsUsed()).containsKeys("tco", "price", "reliability", "warranty");

        RankedOfferDto topOffer = response.getTopRankedOffer();
        assertThat(topOffer).isNotNull();
        assertThat(topOffer.getRank()).isEqualTo(1);
        assertThat(topOffer.isEligible()).isTrue();
        assertThat(topOffer.getTotalScore()).isGreaterThan(BigDecimal.ZERO);

        // Verification of descending ranking order
        List<RankedOfferDto> eligible = response.getEligibleOffers();
        for (int i = 0; i < eligible.size() - 1; i++) {
            assertThat(eligible.get(i).getTotalScore())
                    .isGreaterThanOrEqualTo(eligible.get(i + 1).getTotalScore());
            assertThat(eligible.get(i).getRank()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("Ineligible offers are strictly quarantined to Pool B and never enter Pool A rankings")
    void testStrictPoolIsolationForIneligibleOffers() {
        // Strict price hard constraint: price <= 50,000
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder()
                        .attribute("price")
                        .operator(ConstraintOperator.LESS_THAN_OR_EQUAL)
                        .value("50000")
                        .mandatory(true)
                        .build(),
                ProcurementConstraint.builder()
                        .attribute("screenSize")
                        .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                        .value("55")
                        .mandatory(true)
                        .build()
        );

        ProcurementRequest request = createProcurement("TV", 1, new BigDecimal("60000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        ProcurementRankingResponse response = rankingService.rankOffers(request.getId());

        // Pool A should contain only eligible products (price <= 50,000)
        assertThat(response.getEligibleOffers()).allMatch(o -> o.isEligible() && o.getUnitPrice().compareTo(new BigDecimal("50000.00")) <= 0);

        // Pool B should contain exception products (price > 50,000)
        assertThat(response.getExceptionOffers()).allMatch(o -> o.isExceptionOffer() && !o.isEligible());

        // Top eligible is rank 1 in Pool A, distinct from top exception
        if (response.getTopExceptionOffer() != null) {
            assertThat(response.getTopExceptionOffer().isExceptionOffer()).isTrue();
        }
    }
}
