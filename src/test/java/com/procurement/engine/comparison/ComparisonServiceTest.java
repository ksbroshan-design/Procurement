package com.procurement.engine.comparison;

import com.procurement.engine.comparison.model.ProductComparisonItemDto;
import com.procurement.engine.comparison.model.ProcurementComparisonResponse;
import com.procurement.engine.comparison.service.ComparisonService;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
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
class ComparisonServiceTest {

    @Autowired
    private ComparisonService comparisonService;

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
    @DisplayName("Produces normalized side-by-side comparison across candidate TV offers")
    void testCompareTvCandidates() {
        List<ProcurementConstraint> constraints = List.of(
                // Hard constraint: Screen size >= 55
                ProcurementConstraint.builder()
                        .attribute("screenSize")
                        .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                        .value("55")
                        .mandatory(true)
                        .build(),
                // Soft preference: OLED panel
                ProcurementConstraint.builder()
                        .attribute("panelType")
                        .operator(ConstraintOperator.EQUALS)
                        .value("OLED")
                        .mandatory(false)
                        .weight(new BigDecimal("0.50"))
                        .build()
        );

        ProcurementRequest req = createProcurement("TV", 5, new BigDecimal("350000.00"), constraints);

        ProcurementComparisonResponse response = comparisonService.compareCandidates(req.getId());

        assertThat(response.getProcurementId()).isEqualTo(req.getId());
        assertThat(response.getCategory()).isEqualTo("TV");
        assertThat(response.getTotalCandidatesCompared()).isGreaterThanOrEqualTo(4);
        assertThat(response.getMinPrice()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getMaxPrice()).isGreaterThanOrEqualTo(response.getMinPrice());
        assertThat(response.getFastestDeliveryDays()).isGreaterThan(0);
        assertThat(response.getHighestSellerRating()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getHighestReliabilityScore()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getRejectionCount()).isGreaterThan(0);

        List<ProductComparisonItemDto> offers = response.getOffers();
        assertThat(offers).isNotEmpty();

        for (ProductComparisonItemDto item : offers) {
            assertThat(item.getOfferId()).isNotNull();
            assertThat(item.getProductId()).isNotNull();
            assertThat(item.getProductName()).isNotNull();
            assertThat(item.getVendorName()).isNotNull();
            assertThat(item.getPrice()).isNotNull();
            assertThat(item.getSpecifications()).isNotEmpty();
            assertThat(item.isEligible()).isTrue();
        }
    }

    @Test
    @DisplayName("Handles zero eligible candidates cleanly")
    void testCompareZeroEligibleCandidates() {
        // Impossible constraint
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder()
                        .attribute("price")
                        .operator(ConstraintOperator.LESS_THAN)
                        .value("100")
                        .mandatory(true)
                        .build()
        );

        ProcurementRequest req = createProcurement("Office chair", 5, new BigDecimal("50000.00"), constraints);

        ProcurementComparisonResponse response = comparisonService.compareCandidates(req.getId());

        assertThat(response.getTotalCandidatesCompared()).isEqualTo(0);
        assertThat(response.getOffers()).isEmpty();
        assertThat(response.getRejectionCount()).isGreaterThan(0);
    }
}
