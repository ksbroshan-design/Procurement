package com.procurement.engine.approval;

import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.service.ApprovalService;
import com.procurement.engine.authorization.model.ApprovalActionRequest;
import com.procurement.engine.authorization.model.ApprovalResponseDto;
import com.procurement.engine.authorization.service.AuthorizationService;
import com.procurement.engine.common.exception.ApprovalException;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApprovalServiceTest {

    @Autowired
    private ApprovalService approvalService;

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

    private ProcurementRequest createPendingApprovalProcurement() {
        // 6 Dell laptops @ 78k = 468,000 > manager limit 450,000
        ProcurementRequest req = ProcurementRequest.builder()
                .user(manager)
                .category("Laptop")
                .quantity(6)
                .authorizationLimit(new BigDecimal("450000.00"))
                .status(ProcurementState.SUBMITTED)
                .build();

        req.addConstraint(ProcurementConstraint.builder()
                .attribute("ram")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("16")
                .mandatory(true)
                .build());
        req.addConstraint(ProcurementConstraint.builder()
                .attribute("processor")
                .operator(ConstraintOperator.EQUALS)
                .value("Intel Core i7-1365U")
                .mandatory(true)
                .build());

        ProcurementRequest saved = procurementRequestRepository.save(req);
        discoveryService.discoverAndEvaluate(saved.getId());
        authorizationService.checkAuthorization(saved.getId());
        return procurementRequestRepository.findById(saved.getId()).orElseThrow();
    }

    @Autowired
    private com.procurement.engine.purchase.repository.PurchaseOrderRepository purchaseOrderRepository;

    @Test
    @DisplayName("Approves pending procurement decision, resumes orchestration through REVALIDATING to COMPLETED with exactly 1 PurchaseOrder")
    void testApproveTransitionsToRevalidatingAndCompletes() {
        ProcurementRequest request = createPendingApprovalProcurement();
        assertThat(request.getStatus()).isEqualTo(ProcurementState.WAITING_APPROVAL);

        ApprovalResponseDto response = approvalService.approve(request.getId(), ApprovalActionRequest.ofComments("Budget approved by VP"), manager);

        assertThat(response.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.getComments()).isEqualTo("Budget approved by VP");
        assertThat(response.getDecidedByName()).isEqualTo(manager.getName());

        ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcurementState.COMPLETED);

        // Verify exactly one PurchaseOrder was created
        List<com.procurement.engine.purchase.entity.PurchaseOrder> pos = purchaseOrderRepository.findByProcurementId(request.getId());
        assertThat(pos).hasSize(1);
        assertThat(pos.get(0).getStatus()).isEqualTo(com.procurement.engine.purchase.entity.PurchaseOrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Rejects pending procurement decision and transitions state to REJECTED")
    void testRejectTransitionsToRejected() {
        ProcurementRequest request = createPendingApprovalProcurement();
        assertThat(request.getStatus()).isEqualTo(ProcurementState.WAITING_APPROVAL);

        ApprovalResponseDto response = approvalService.reject(request.getId(), ApprovalActionRequest.ofComments("Budget denied"), manager);

        assertThat(response.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(response.getComments()).isEqualTo("Budget denied");

        ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcurementState.REJECTED);
    }

    @Test
    @DisplayName("CRITICAL SECURITY: Blocks arbitrary/mismatched client offer approval")
    void testArbitraryOfferApprovalBlocked() {
        ProcurementRequest request = createPendingApprovalProcurement();
        assertThat(request.getStatus()).isEqualTo(ProcurementState.WAITING_APPROVAL);

        ApprovalResponseDto pending = approvalService.getApproval(request.getId());
        UUID proposedOfferId = pending.getProposedOfferId();
        assertThat(proposedOfferId).isNotNull();

        // Attempt to approve an arbitrary mismatched offer ID
        UUID arbitraryOfferId = UUID.randomUUID();
        ApprovalActionRequest maliciousRequest = new ApprovalActionRequest("Attacking offer selection", arbitraryOfferId);

        assertThatThrownBy(() -> approvalService.approve(request.getId(), maliciousRequest, manager))
                .isInstanceOf(ApprovalException.class)
                .hasMessageContaining("Unauthorized offer selection");

        // Ensure state did NOT change and remains WAITING_APPROVAL
        ProcurementRequest unchanged = procurementRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ProcurementState.WAITING_APPROVAL);
    }

    @Test
    @DisplayName("Replay protection: repeated approval or reject after decision is blocked and no duplicate purchase orders created")
    void testReplayProtection() {
        ProcurementRequest request = createPendingApprovalProcurement();

        // First approval succeeds
        approvalService.approve(request.getId(), ApprovalActionRequest.ofComments("Approved 1"), manager);

        // Subsequent approval attempt must be blocked
        assertThatThrownBy(() -> approvalService.approve(request.getId(), ApprovalActionRequest.ofComments("Approved 2"), manager))
                .isInstanceOf(ApprovalException.class);

        // Reject attempt on already approved decision must be blocked
        assertThatThrownBy(() -> approvalService.reject(request.getId(), ApprovalActionRequest.ofComments("Rejecting"), manager))
                .isInstanceOf(ApprovalException.class);

        // Ensure still exactly 1 PurchaseOrder
        List<com.procurement.engine.purchase.entity.PurchaseOrder> pos = purchaseOrderRepository.findByProcurementId(request.getId());
        assertThat(pos).hasSize(1);
    }
}
