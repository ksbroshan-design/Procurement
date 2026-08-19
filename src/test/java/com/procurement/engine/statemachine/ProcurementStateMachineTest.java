package com.procurement.engine.statemachine;

import com.procurement.engine.common.exception.InvalidStateTransitionException;
import com.procurement.engine.config.EngineProperties;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.statemachine.model.StateTransitionEvent;
import com.procurement.engine.statemachine.model.StateTransitionResult;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProcurementStateMachineTest {

    @Autowired
    private ProcurementStateMachine stateMachine;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EngineProperties engineProperties;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByEmail("manager@procurement.com").orElseThrow();
    }

    private ProcurementRequest createRequest(ProcurementState initialStatus) {
        ProcurementRequest request = ProcurementRequest.builder()
                .user(testUser)
                .category("TV")
                .quantity(5)
                .authorizationLimit(new BigDecimal("300000.00"))
                .status(initialStatus)
                .build();
        return procurementRequestRepository.save(request);
    }

    @Nested
    @DisplayName("Valid State Transition Lifecycle Tests")
    class ValidTransitionTests {

        @Test
        @DisplayName("Full happy path from SUBMITTED through to COMPLETED")
        void testFullHappyPathLifecycle() {
            ProcurementRequest request = createRequest(ProcurementState.SUBMITTED);
            assertThat(request.getStatus()).isEqualTo(ProcurementState.SUBMITTED);

            // 1. SUBMITTED -> VALIDATING
            StateTransitionResult r1 = stateMachine.transition(request, ProcurementState.VALIDATING, "AI_PARSER", "Validating brief", Map.of());
            assertThat(r1.getNewState()).isEqualTo(ProcurementState.VALIDATING);

            // 2. VALIDATING -> SEARCHING
            StateTransitionResult r2 = stateMachine.transition(request, ProcurementState.SEARCHING, "SYSTEM", "Searching vendors", Map.of());
            assertThat(r2.getNewState()).isEqualTo(ProcurementState.SEARCHING);

            // 3. SEARCHING -> EVALUATING
            StateTransitionResult r3 = stateMachine.transition(request, ProcurementState.EVALUATING, "SYSTEM", "Evaluating products", Map.of());
            assertThat(r3.getNewState()).isEqualTo(ProcurementState.EVALUATING);

            // 4. EVALUATING -> TCO_ANALYSIS
            StateTransitionResult r4 = stateMachine.transition(request, ProcurementState.TCO_ANALYSIS, "SYSTEM", "Calculating TCO", Map.of());
            assertThat(r4.getNewState()).isEqualTo(ProcurementState.TCO_ANALYSIS);

            // 5. TCO_ANALYSIS -> RECOMMENDED
            StateTransitionResult r5 = stateMachine.transition(request, ProcurementState.RECOMMENDED, "SYSTEM", "Generating recommendation", Map.of());
            assertThat(r5.getNewState()).isEqualTo(ProcurementState.RECOMMENDED);

            // 6. RECOMMENDED -> NEGOTIATING
            StateTransitionResult r6 = stateMachine.transition(request, ProcurementState.NEGOTIATING, "SYSTEM", "Starting negotiation", Map.of());
            assertThat(r6.getNewState()).isEqualTo(ProcurementState.NEGOTIATING);

            // 7. NEGOTIATING -> AUTHORIZATION_CHECK
            StateTransitionResult r7 = stateMachine.transition(request, ProcurementState.AUTHORIZATION_CHECK, "SYSTEM", "Checking limits", Map.of());
            assertThat(r7.getNewState()).isEqualTo(ProcurementState.AUTHORIZATION_CHECK);

            // 8. AUTHORIZATION_CHECK -> REVALIDATING (within limit)
            StateTransitionResult r8 = stateMachine.handleAuthorizationOutcome(request, true, "AUTH_ENGINE", "Within limit", Map.of());
            assertThat(r8.getNewState()).isEqualTo(ProcurementState.REVALIDATING);

            // 9. REVALIDATING -> PURCHASING (revalidation passed)
            StateTransitionResult r9 = stateMachine.handleRevalidationOutcome(request, true, "REVAL_ENGINE", "Passed", Map.of());
            assertThat(r9.getNewState()).isEqualTo(ProcurementState.PURCHASING);

            // 10. PURCHASING -> COMPLETED
            StateTransitionResult r10 = stateMachine.handlePurchaseOutcome(request, true, "PURCHASE_ENGINE", "PO confirmed", Map.of());
            assertThat(r10.getNewState()).isEqualTo(ProcurementState.COMPLETED);
            assertThat(request.getStatus()).isEqualTo(ProcurementState.COMPLETED);
        }

        @Test
        @DisplayName("RECOMMENDED can transition directly to AUTHORIZATION_CHECK if negotiation is skipped")
        void testRecommendedDirectToAuthorizationCheck() {
            ProcurementRequest request = createRequest(ProcurementState.RECOMMENDED);
            StateTransitionResult result = stateMachine.transition(request, ProcurementState.AUTHORIZATION_CHECK, "SYSTEM", "Skip negotiation", Map.of());
            assertThat(result.getNewState()).isEqualTo(ProcurementState.AUTHORIZATION_CHECK);
        }
    }

    @Nested
    @DisplayName("Bypass Protection & Invalid Transition Tests")
    class BypassProtectionTests {

        @Test
        @DisplayName("Direct purchase bypass from SUBMITTED is blocked")
        void testDirectPurchaseBypassFromSubmitted() {
            ProcurementRequest request = createRequest(ProcurementState.SUBMITTED);
            assertThatThrownBy(() -> stateMachine.transition(request, ProcurementState.PURCHASING, "ATTACKER", "Direct purchase bypass", Map.of()))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Invalid state transition from [SUBMITTED] to [PURCHASING]");
        }

        @Test
        @DisplayName("Direct completion bypass from SUBMITTED is blocked")
        void testDirectCompletionBypassFromSubmitted() {
            ProcurementRequest request = createRequest(ProcurementState.SUBMITTED);
            assertThatThrownBy(() -> stateMachine.transition(request, ProcurementState.COMPLETED, "ATTACKER", "Direct complete bypass", Map.of()))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Invalid state transition from [SUBMITTED] to [COMPLETED]");
        }

        @Test
        @DisplayName("Direct purchase bypass from RECOMMENDED is blocked")
        void testDirectPurchaseBypassFromRecommended() {
            ProcurementRequest request = createRequest(ProcurementState.RECOMMENDED);
            assertThatThrownBy(() -> stateMachine.transition(request, ProcurementState.PURCHASING, "ATTACKER", "Bypass check", Map.of()))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("Direct purchase bypass from AUTHORIZATION_CHECK is blocked")
        void testDirectPurchaseBypassFromAuthorizationCheck() {
            ProcurementRequest request = createRequest(ProcurementState.AUTHORIZATION_CHECK);
            assertThatThrownBy(() -> stateMachine.transition(request, ProcurementState.PURCHASING, "ATTACKER", "Bypass revalidation", Map.of()))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("Direct purchase bypass from WAITING_APPROVAL is blocked")
        void testDirectPurchaseBypassFromWaitingApproval() {
            ProcurementRequest request = createRequest(ProcurementState.WAITING_APPROVAL);
            assertThatThrownBy(() -> stateMachine.transition(request, ProcurementState.PURCHASING, "ATTACKER", "Bypass approval and revalidation", Map.of()))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("Terminal states COMPLETED and REJECTED cannot transition further")
        void testTerminalStatesCannotTransition() {
            ProcurementRequest completed = createRequest(ProcurementState.COMPLETED);
            assertThat(stateMachine.getValidNextStates(ProcurementState.COMPLETED)).isEmpty();
            assertThatThrownBy(() -> stateMachine.transition(completed, ProcurementState.SUBMITTED, "USER", "Restart completed", Map.of()))
                    .isInstanceOf(InvalidStateTransitionException.class);

            ProcurementRequest rejected = createRequest(ProcurementState.REJECTED);
            assertThat(stateMachine.getValidNextStates(ProcurementState.REJECTED)).isEmpty();
            assertThatThrownBy(() -> stateMachine.transition(rejected, ProcurementState.PURCHASING, "USER", "Force purchase rejected", Map.of()))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Authorization & Human Approval Escalation Tests")
    class AuthorizationAndApprovalTests {

        @Test
        @DisplayName("Exceeding authorization limit transitions to WAITING_APPROVAL (never automatically REJECTED)")
        void testExceedingAuthorizationLimitEscalatesToWaitingApproval() {
            ProcurementRequest request = createRequest(ProcurementState.AUTHORIZATION_CHECK);

            StateTransitionResult result = stateMachine.handleAuthorizationOutcome(
                    request, false, "AUTHORIZATION_ENGINE", "Amount exceeds user limit", Map.of()
            );

            assertThat(result.getNewState()).isEqualTo(ProcurementState.WAITING_APPROVAL);
            assertThat(request.getStatus()).isEqualTo(ProcurementState.WAITING_APPROVAL);
        }

        @Test
        @DisplayName("Human approval grants transition from WAITING_APPROVAL to REVALIDATING")
        void testHumanApprovalApproved() {
            ProcurementRequest request = createRequest(ProcurementState.WAITING_APPROVAL);

            StateTransitionResult result = stateMachine.handleApprovalDecision(
                    request, true, "sarah.admin@procurement.com", "Approved budget increase", Map.of()
            );

            assertThat(result.getNewState()).isEqualTo(ProcurementState.REVALIDATING);
            assertThat(request.getStatus()).isEqualTo(ProcurementState.REVALIDATING);
        }

        @Test
        @DisplayName("Explicit human rejection produces REJECTED")
        void testHumanApprovalRejected() {
            ProcurementRequest request = createRequest(ProcurementState.WAITING_APPROVAL);

            StateTransitionResult result = stateMachine.handleApprovalDecision(
                    request, false, "sarah.admin@procurement.com", "Budget request denied", Map.of()
            );

            assertThat(result.getNewState()).isEqualTo(ProcurementState.REJECTED);
            assertThat(request.getStatus()).isEqualTo(ProcurementState.REJECTED);
        }
    }

    @Nested
    @DisplayName("Revalidation Retry Management Tests")
    class RevalidationRetryTests {

        @Test
        @DisplayName("Stale offer revalidation failure retries back to SEARCHING while attempts < maxRetries")
        void testRevalidationRetriesBackToSearching() {
            ProcurementRequest request = createRequest(ProcurementState.REVALIDATING);
            request.setRevalidationAttempts(0);
            int maxRetries = engineProperties.getRevalidation().getMaxRetryAttempts();
            assertThat(maxRetries).isEqualTo(3);

            // Attempt 1: 0 -> 1 -> SEARCHING
            StateTransitionResult r1 = stateMachine.handleRevalidationOutcome(request, false, "REVAL_ENGINE", "Stale price", Map.of());
            assertThat(r1.getNewState()).isEqualTo(ProcurementState.SEARCHING);
            assertThat(request.getRevalidationAttempts()).isEqualTo(1);

            // Simulate progressing back to REVALIDATING
            stateMachine.transition(request, ProcurementState.EVALUATING, "SYS", "Re-eval", Map.of());
            stateMachine.transition(request, ProcurementState.TCO_ANALYSIS, "SYS", "TCO", Map.of());
            stateMachine.transition(request, ProcurementState.RECOMMENDED, "SYS", "Rec", Map.of());
            stateMachine.transition(request, ProcurementState.AUTHORIZATION_CHECK, "SYS", "Auth", Map.of());
            stateMachine.transition(request, ProcurementState.REVALIDATING, "SYS", "Reval", Map.of());

            // Attempt 2: 1 -> 2 -> SEARCHING
            StateTransitionResult r2 = stateMachine.handleRevalidationOutcome(request, false, "REVAL_ENGINE", "Vendor stock out", Map.of());
            assertThat(r2.getNewState()).isEqualTo(ProcurementState.SEARCHING);
            assertThat(request.getRevalidationAttempts()).isEqualTo(2);

            // Simulate progressing back to REVALIDATING
            stateMachine.transition(request, ProcurementState.EVALUATING, "SYS", "Re-eval", Map.of());
            stateMachine.transition(request, ProcurementState.TCO_ANALYSIS, "SYS", "TCO", Map.of());
            stateMachine.transition(request, ProcurementState.RECOMMENDED, "SYS", "Rec", Map.of());
            stateMachine.transition(request, ProcurementState.AUTHORIZATION_CHECK, "SYS", "Auth", Map.of());
            stateMachine.transition(request, ProcurementState.REVALIDATING, "SYS", "Reval", Map.of());

            // Attempt 3: 2 -> 3 -> SEARCHING
            StateTransitionResult r3 = stateMachine.handleRevalidationOutcome(request, false, "REVAL_ENGINE", "Price changed", Map.of());
            assertThat(r3.getNewState()).isEqualTo(ProcurementState.SEARCHING);
            assertThat(request.getRevalidationAttempts()).isEqualTo(3);

            // Simulate progressing back to REVALIDATING
            stateMachine.transition(request, ProcurementState.EVALUATING, "SYS", "Re-eval", Map.of());
            stateMachine.transition(request, ProcurementState.TCO_ANALYSIS, "SYS", "TCO", Map.of());
            stateMachine.transition(request, ProcurementState.RECOMMENDED, "SYS", "Rec", Map.of());
            stateMachine.transition(request, ProcurementState.AUTHORIZATION_CHECK, "SYS", "Auth", Map.of());
            stateMachine.transition(request, ProcurementState.REVALIDATING, "SYS", "Reval", Map.of());

            // Attempt 4: 3 >= maxRetries (3) -> WAITING_USER (no infinite loop)
            StateTransitionResult r4 = stateMachine.handleRevalidationOutcome(request, false, "REVAL_ENGINE", "Still unavailable", Map.of());
            assertThat(r4.getNewState()).isEqualTo(ProcurementState.WAITING_USER);
            assertThat(request.getStatus()).isEqualTo(ProcurementState.WAITING_USER);
        }
    }

    @Nested
    @DisplayName("State Transition Event Decoupling Tests")
    class StateTransitionEventTests {

        @Test
        @DisplayName("State transition produces complete decoupled StateTransitionEvent")
        void testEventEmittedWithCorrectDetails() {
            ProcurementRequest request = createRequest(ProcurementState.SUBMITTED);
            Map<String, Object> meta = Map.of("trigger", "automated_batch", "priority", "HIGH");

            StateTransitionResult result = stateMachine.transition(request, ProcurementState.VALIDATING, "PARSER_AGENT", "Requirements extracted", meta);

            StateTransitionEvent event = result.getEvent();
            assertThat(event).isNotNull();
            assertThat(event.getProcurementId()).isEqualTo(request.getId());
            assertThat(event.getPreviousState()).isEqualTo(ProcurementState.SUBMITTED);
            assertThat(event.getNewState()).isEqualTo(ProcurementState.VALIDATING);
            assertThat(event.getActor()).isEqualTo("PARSER_AGENT");
            assertThat(event.getReason()).isEqualTo("Requirements extracted");
            assertThat(event.getMetadata()).containsEntry("priority", "HIGH");
            assertThat(event.getTimestamp()).isNotNull();
        }
    }
}
