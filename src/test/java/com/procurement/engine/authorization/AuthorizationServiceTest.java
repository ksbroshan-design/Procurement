package com.procurement.engine.authorization;

import com.procurement.engine.approval.entity.Approval;
import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.repository.ApprovalRepository;
import com.procurement.engine.approval.service.ApprovalService;
import com.procurement.engine.authorization.model.ApprovalActionRequest;
import com.procurement.engine.authorization.model.AuthorizationDecisionDto;
import com.procurement.engine.authorization.service.AuthorizationService;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.model.OrchestrationResultDto;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.service.ProcurementOrchestrator;
import com.procurement.engine.purchase.entity.PurchaseOrder;
import com.procurement.engine.purchase.repository.PurchaseOrderRepository;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.Role;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthorizationServiceTest {

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ProcurementOrchestrator procurementOrchestrator;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    private User manager;

    @BeforeEach
    void setUp() {
        manager = userRepository.findByEmail("manager@procurement.com").orElseThrow();
    }

    private ProcurementRequest createProcurement(User user, String category, int quantity, BigDecimal limit, List<ProcurementConstraint> constraints) {
        ProcurementRequest req = ProcurementRequest.builder()
                .user(user)
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
        @DisplayName("1. Purchase below user limit: ₹390,000 <= ₹450,000 -> AUTO_AUTHORIZED")
        void testPurchaseBelowUserLimitAutoAuthorized() {
            // 5 Dell laptops @ 78k = 390,000 <= manager limit 450,000
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
            );

            ProcurementRequest request = createProcurement(manager, "Laptop", 5, new BigDecimal("450000.00"), constraints);
            discoveryService.discoverAndEvaluate(request.getId());

            AuthorizationDecisionDto decision = authorizationService.checkAuthorization(request.getId());

            assertThat(decision.isWithinAuthorization()).isTrue();
            assertThat(decision.getDecision()).isEqualTo("AUTO_AUTHORIZED");
            assertThat(decision.getNextState()).isEqualTo("REVALIDATING");
            assertThat(decision.getTotalRequestedAmount()).isEqualByComparingTo("390000.00");
            assertThat(decision.getAuthorizationLimit()).isEqualByComparingTo("450000.00");
            assertThat(decision.getExcessAmount()).isEqualByComparingTo("0.00");

            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.REVALIDATING);
        }

        @Test
        @DisplayName("2. Purchase exactly at user limit: ₹390,000 <= ₹390,000 -> AUTO_AUTHORIZED")
        void testPurchaseExactlyAtUserLimitAutoAuthorized() {
            User exactLimitUser = User.builder()
                    .name("Exact Limit User")
                    .email("exact@procurement.com")
                    .password("pass")
                    .role(Role.USER)
                    .authorizationLimit(new BigDecimal("390000.00"))
                    .build();
            userRepository.save(exactLimitUser);

            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
            );

            ProcurementRequest request = createProcurement(exactLimitUser, "Laptop", 5, new BigDecimal("390000.00"), constraints);
            discoveryService.discoverAndEvaluate(request.getId());

            AuthorizationDecisionDto decision = authorizationService.checkAuthorization(request.getId());

            assertThat(decision.isWithinAuthorization()).isTrue();
            assertThat(decision.getDecision()).isEqualTo("AUTO_AUTHORIZED");
            assertThat(decision.getNextState()).isEqualTo("REVALIDATING");
            assertThat(decision.getTotalRequestedAmount()).isEqualByComparingTo("390000.00");
            assertThat(decision.getAuthorizationLimit()).isEqualByComparingTo("390000.00");
        }
    }

    @Nested
    @DisplayName("Exceeding Authority & Human Approval Tests")
    class ExceedingAuthorityTests {

        @Test
        @DisplayName("3. Purchase above user limit: ₹468,000 > ₹450,000 -> REQUIRES_APPROVAL -> WAITING_APPROVAL")
        void testPurchaseAboveUserLimitEscalatesToWaitingApproval() {
            // 6 Dell laptops @ 78k = 468,000 > manager limit 450,000
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build(),
                    ProcurementConstraint.builder().attribute("processor").operator(ConstraintOperator.EQUALS).value("Intel Core i7-1365U").mandatory(true).build()
            );

            ProcurementRequest request = createProcurement(manager, "Laptop", 6, new BigDecimal("450000.00"), constraints);
            discoveryService.discoverAndEvaluate(request.getId());

            AuthorizationDecisionDto decision = authorizationService.checkAuthorization(request.getId());

            assertThat(decision.isWithinAuthorization()).isFalse();
            assertThat(decision.getDecision()).isEqualTo("REQUIRES_APPROVAL");
            assertThat(decision.getNextState()).isEqualTo("WAITING_APPROVAL");
            assertThat(decision.getExceptionType()).isEqualTo("LIMIT_EXCEEDED");
            assertThat(decision.getTotalRequestedAmount()).isEqualByComparingTo("468000.00");
            assertThat(decision.getAuthorizationLimit()).isEqualByComparingTo("450000.00");
            assertThat(decision.getExcessAmount()).isEqualByComparingTo("18000.00");

            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.WAITING_APPROVAL);

            List<Approval> approvals = approvalRepository.findByProcurementId(request.getId());
            assertThat(approvals).hasSize(1);
            assertThat(approvals.get(0).getStatus()).isEqualTo(ApprovalStatus.PENDING);
            assertThat(approvals.get(0).getRequestedAmount()).isEqualByComparingTo("468000.00");
            assertThat(approvals.get(0).getAuthorizationLimit()).isEqualByComparingTo("450000.00");
            assertThat(approvals.get(0).getDifference()).isEqualByComparingTo("18000.00");
        }

        @Test
        @DisplayName("4. Critical regression test: Request authorizationLimit ₹600,000 cannot override user limit ₹450,000")
        void testProcurementRequestLargerAuthorizationLimitDoesNotOverrideUserLimit() {
            // Requester attempts to inflate limit to 600,000 in request body, but manager limit is 450,000
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build(),
                    ProcurementConstraint.builder().attribute("processor").operator(ConstraintOperator.EQUALS).value("Intel Core i7-1365U").mandatory(true).build()
            );

            ProcurementRequest request = createProcurement(manager, "Laptop", 6, new BigDecimal("600000.00"), constraints);
            discoveryService.discoverAndEvaluate(request.getId());

            AuthorizationDecisionDto decision = authorizationService.checkAuthorization(request.getId());

            // Must evaluate against user limit 450,000 (468,000 > 450,000) -> REQUIRES_APPROVAL
            assertThat(decision.isWithinAuthorization()).isFalse();
            assertThat(decision.getDecision()).isEqualTo("REQUIRES_APPROVAL");
            assertThat(decision.getNextState()).isEqualTo("WAITING_APPROVAL");
            assertThat(decision.getAuthorizationLimit()).isEqualByComparingTo("450000.00");
            assertThat(decision.getTotalRequestedAmount()).isEqualByComparingTo("468000.00");
            assertThat(decision.getExcessAmount()).isEqualByComparingTo("18000.00");

            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.WAITING_APPROVAL);
        }

        @Test
        @DisplayName("5. Fail closed when user is null or user has null authorization limit")
        void testNullUserAuthorizationLimitFailsClosed() {
            ProcurementRequest requestNoUser = ProcurementRequest.builder()
                    .user(null)
                    .category("Laptop")
                    .quantity(1)
                    .build();
            org.springframework.test.util.ReflectionTestUtils.setField(requestNoUser, "id", java.util.UUID.randomUUID());

            assertThatThrownBy(() -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(authorizationService, "resolveEffectiveLimit", requestNoUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot determine authorization user for procurement");

            User nullLimitUser = User.builder()
                    .name("Transient Null Limit")
                    .email("null_transient@procurement.com")
                    .password("pass")
                    .role(Role.USER)
                    .build();
            nullLimitUser.setAuthorizationLimit(null);

            ProcurementRequest requestNullLimit = ProcurementRequest.builder()
                    .user(nullLimitUser)
                    .category("Laptop")
                    .quantity(1)
                    .build();
            org.springframework.test.util.ReflectionTestUtils.setField(requestNullLimit, "id", java.util.UUID.randomUUID());

            assertThatThrownBy(() -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(authorizationService, "resolveEffectiveLimit", requestNullLimit))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("User authorization limit is not configured");
        }

        @Test
        @DisplayName("6. Fail closed when user has negative authorization limit")
        void testNegativeUserAuthorizationLimitFailsClosed() {
            User negativeLimitUser = User.builder()
                    .name("Transient Negative Limit")
                    .email("neg_transient@procurement.com")
                    .password("pass")
                    .role(Role.USER)
                    .build();
            negativeLimitUser.setAuthorizationLimit(new BigDecimal("-100.00"));

            ProcurementRequest request = ProcurementRequest.builder()
                    .user(negativeLimitUser)
                    .category("Laptop")
                    .quantity(1)
                    .build();
            org.springframework.test.util.ReflectionTestUtils.setField(request, "id", java.util.UUID.randomUUID());

            assertThatThrownBy(() -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(authorizationService, "resolveEffectiveLimit", request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("User authorization limit cannot be negative");
        }

        @Test
        @DisplayName("7. Idempotent evaluation: repeated checkAuthorization creates exactly one PENDING approval")
        void testRepeatedAuthorizationEvaluationIdempotency() {
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build(),
                    ProcurementConstraint.builder().attribute("processor").operator(ConstraintOperator.EQUALS).value("Intel Core i7-1365U").mandatory(true).build()
            );

            ProcurementRequest request = createProcurement(manager, "Laptop", 6, new BigDecimal("450000.00"), constraints);
            discoveryService.discoverAndEvaluate(request.getId());

            // First check
            authorizationService.checkAuthorization(request.getId());
            // Second check (repeat)
            authorizationService.checkAuthorization(request.getId());

            List<Approval> approvals = approvalRepository.findByProcurementId(request.getId());
            assertThat(approvals).hasSize(1);
            assertThat(approvals.get(0).getStatus()).isEqualTo(ApprovalStatus.PENDING);
        }

        @Test
        @DisplayName("8. After manager approval, workflow reaches COMPLETED with exactly one PurchaseOrder")
        void testApprovalWorkflowCompletesPurchaseWithSinglePurchaseOrder() {
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build(),
                    ProcurementConstraint.builder().attribute("processor").operator(ConstraintOperator.EQUALS).value("Intel Core i7-1365U").mandatory(true).build()
            );

            ProcurementRequest request = createProcurement(manager, "Laptop", 6, new BigDecimal("450000.00"), constraints);
            
            // 1. Initial Orchestration stops at WAITING_APPROVAL
            OrchestrationResultDto step1 = procurementOrchestrator.orchestrate(request.getId());
            assertThat(step1.getFinalState()).isEqualTo(ProcurementState.WAITING_APPROVAL);

            // 2. Manager approves (automatically resumes orchestration to completion)
            User admin = userRepository.findByEmail("admin@procurement.com").orElseThrow();
            approvalService.approve(request.getId(), ApprovalActionRequest.ofComments("Approved by manager"), admin);

            ProcurementRequest afterApproval = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(afterApproval.getStatus()).isEqualTo(ProcurementState.COMPLETED);

            // 3. Resume orchestration to complete purchase (idempotency check)
            OrchestrationResultDto step2 = procurementOrchestrator.orchestrate(request.getId());
            assertThat(step2.getFinalState()).isEqualTo(ProcurementState.COMPLETED);

            // 4. Verify exactly one PurchaseOrder created
            List<PurchaseOrder> orders = purchaseOrderRepository.findByProcurementId(request.getId());
            assertThat(orders).hasSize(1);
            assertThat(orders.get(0).getStatus()).isEqualTo(com.procurement.engine.purchase.entity.PurchaseOrderStatus.CONFIRMED);
            assertThat(orders.get(0).getQuantity()).isEqualTo(6);
            assertThat(orders.get(0).getTotalAmount()).isEqualByComparingTo("468000.00");
        }
    }
}

