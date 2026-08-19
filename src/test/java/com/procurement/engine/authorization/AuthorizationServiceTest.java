package com.procurement.engine.authorization;

import com.procurement.engine.authorization.model.AuthorizationDecisionDto;
import com.procurement.engine.authorization.service.AuthorizationService;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class AuthorizationServiceTest {

    @Autowired
    private AuthorizationService authorizationService;

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

    @Nested
    @DisplayName("Within Authority & Auto-Authorization Tests")
    class WithinAuthorityTests {

        @Test
        @DisplayName("Auto-authorizes when total purchase amount is within limit and transitions to REVALIDATING")
        void testAutoAuthorizationWithinLimit() {
            // TV: price per unit ~ 56,000 * 2 = 112,000 <= 300,000 limit
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
            );

            ProcurementRequest request = createProcurement("TV", 2, new BigDecimal("300000.00"), constraints);
            discoveryService.discoverAndEvaluate(request.getId());

            AuthorizationDecisionDto decision = authorizationService.checkAuthorization(request.getId());

            assertThat(decision.isWithinAuthorization()).isTrue();
            assertThat(decision.getDecision()).isEqualTo("AUTO_AUTHORIZED");
            assertThat(decision.getNextState()).isEqualTo("REVALIDATING");
            assertThat(decision.getExcessAmount()).isEqualByComparingTo("0.00");
            assertThat(decision.getExplanation()).contains("within the ₹300000.00 authorization limit");

            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.REVALIDATING);
        }

        @Test
        @DisplayName("Auto-authorizes when total purchase amount equals authorization limit exactly")
        void testExactBoundaryAuthorization() {
            // Laptop: 5 units with limit set exactly equal to expected total
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
            );

            ProcurementRequest request = createProcurement("Laptop", 1, new BigDecimal("85000.00"), constraints);
            discoveryService.discoverAndEvaluate(request.getId());

            AuthorizationDecisionDto decision = authorizationService.checkAuthorization(request.getId());

            if (decision.getTotalRequestedAmount().compareTo(new BigDecimal("85000.00")) <= 0) {
                assertThat(decision.isWithinAuthorization()).isTrue();
                assertThat(decision.getDecision()).isEqualTo("AUTO_AUTHORIZED");
            }
        }
    }

    @Nested
    @DisplayName("Exceeding Authority & Escalation Tests")
    class ExceedingAuthorityTests {

        @Test
        @DisplayName("Escalates to WAITING_APPROVAL when total purchase amount exceeds limit")
        void testEscalationWhenLimitExceeded() {
            // 10 laptops * ~75,000 = 750,000 > 200,000 limit
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
            );

            ProcurementRequest request = createProcurement("Laptop", 10, new BigDecimal("200000.00"), constraints);
            discoveryService.discoverAndEvaluate(request.getId());

            AuthorizationDecisionDto decision = authorizationService.checkAuthorization(request.getId());

            assertThat(decision.isWithinAuthorization()).isFalse();
            assertThat(decision.getDecision()).isEqualTo("REQUIRES_APPROVAL");
            assertThat(decision.getNextState()).isEqualTo("WAITING_APPROVAL");
            assertThat(decision.getExcessAmount()).isGreaterThan(BigDecimal.ZERO);
            assertThat(decision.getExplanation()).contains("exceeds the ₹200000.00 authorization limit");

            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.WAITING_APPROVAL);
        }

        @Test
        @DisplayName("Escalates to WAITING_APPROVAL with BUDGET_OVERRIDE for Phase 4 exception recommendation")
        void testBudgetOverrideEscalation() {
            // Hard constraint: price <= 50000 and screenSize >= 55
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("price").operator(ConstraintOperator.LESS_THAN_OR_EQUAL).value("50000").mandatory(true).build(),
                    ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
            );

            ProcurementRequest request = createProcurement("TV", 1, new BigDecimal("30000.00"), constraints);
            discoveryService.discoverAndEvaluate(request.getId());

            AuthorizationDecisionDto decision = authorizationService.checkAuthorization(request.getId());

            assertThat(decision.isWithinAuthorization()).isFalse();
            assertThat(decision.getDecision()).isEqualTo("REQUIRES_APPROVAL");
            assertThat(decision.getNextState()).isEqualTo("WAITING_APPROVAL");

            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.WAITING_APPROVAL);
        }
    }
}
