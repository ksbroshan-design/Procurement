package com.procurement.engine.approval.service;

import com.procurement.engine.approval.entity.Approval;
import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.repository.ApprovalRepository;
import com.procurement.engine.authorization.model.ApprovalActionRequest;
import com.procurement.engine.authorization.model.ApprovalResponseDto;
import com.procurement.engine.common.exception.ApprovalException;
import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.procurement.entity.OfferStatus;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.repository.VendorOfferRepository;
import com.procurement.engine.procurement.service.ProcurementOrchestrator;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.statemachine.ProcurementStateMachine;
import com.procurement.engine.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Human-in-the-Loop Approval Service.
 * <p>
 * Enforces server-authoritative offer approval, replay protection, and lifecycle state transitions.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRepository approvalRepository;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final VendorOfferRepository vendorOfferRepository;
    private final ProcurementStateMachine stateMachine;
    private final ProcurementOrchestrator procurementOrchestrator;

    public ApprovalService(ApprovalRepository approvalRepository,
                           ProcurementRequestRepository procurementRequestRepository,
                           VendorOfferRepository vendorOfferRepository,
                           ProcurementStateMachine stateMachine,
                           ProcurementOrchestrator procurementOrchestrator) {
        this.approvalRepository = approvalRepository;
        this.procurementRequestRepository = procurementRequestRepository;
        this.vendorOfferRepository = vendorOfferRepository;
        this.stateMachine = stateMachine;
        this.procurementOrchestrator = procurementOrchestrator;
    }

    /**
     * Retrieves the active or latest approval for a procurement.
     */
    @Transactional(readOnly = true)
    public ApprovalResponseDto getApproval(UUID procurementId) {
        Approval approval = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("No approval record found for procurement: " + procurementId));

        return toDto(approval);
    }

    /**
     * Retrieves all pending approval records across all procurements.
     */
    @Transactional(readOnly = true)
    public java.util.List<ApprovalResponseDto> getPendingApprovals() {
        return approvalRepository.findByStatus(ApprovalStatus.PENDING)
                .stream()
                .map(this::toDto)
                .toList();
    }


    /**
     * Approves a pending procurement decision.
     * Transitions state from WAITING_APPROVAL to REVALIDATING.
     */
    @Transactional
    public ApprovalResponseDto approve(UUID procurementId, ApprovalActionRequest requestPayload, User approver) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        if (request.getStatus() != ProcurementState.WAITING_APPROVAL) {
            throw new ApprovalException("Cannot approve procurement. Current state is [" + request.getStatus() + "], expected [WAITING_APPROVAL].");
        }

        Approval approval = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("No pending approval found for procurement: " + procurementId));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new ApprovalException("Approval decision has already been reached with status [" + approval.getStatus() + "]. Replay blocked.");
        }

        // CRITICAL SECURITY ASSERTION: Prevent client-side arbitrary offer switching
        if (requestPayload != null && requestPayload.getApprovedOfferId() != null) {
            UUID pendingOfferId = approval.getProposedOffer() != null ? approval.getProposedOffer().getId() : null;
            if (pendingOfferId != null && !pendingOfferId.equals(requestPayload.getApprovedOfferId())) {
                log.error("Security violation: Attempted to approve mismatched offer [{}] instead of pending proposed offer [{}]",
                        requestPayload.getApprovedOfferId(), pendingOfferId);
                throw new ApprovalException("Unauthorized offer selection: Submitted offer ID does not match server-persisted pending proposed offer.");
            }
        }

        // Apply approval
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setDecidedAt(Instant.now());
        approval.setDecidedBy(approver);
        approval.setComments(requestPayload != null && requestPayload.getComments() != null ? requestPayload.getComments() : "Approved by manager");

        // If a proposed exception offer was approved, bind it as the selected offer
        VendorOffer approvedOffer = approval.getProposedOffer();
        if (approvedOffer != null) {
            request.setSelectedOffer(approvedOffer);
            request.setSelectedProduct(approvedOffer.getProduct());
            approvedOffer.setStatus(OfferStatus.ACCEPTED);
            vendorOfferRepository.save(approvedOffer);
            procurementRequestRepository.save(request);
        }

        approvalRepository.save(approval);

        // Transition state: WAITING_APPROVAL -> REVALIDATING
        stateMachine.handleApprovalDecision(request, true,
                approver != null ? approver.getName() : "MANAGER",
                approval.getComments(),
                Map.of("approvalId", approval.getId().toString(), "approvedAmount", approval.getRequestedAmount().toString()));

        log.info("Procurement [{}] APPROVED by [{}]. Transitioned to REVALIDATING.", procurementId, approver != null ? approver.getEmail() : "MANAGER");

        // Resume deterministic orchestration to advance REVALIDATING -> PURCHASING -> COMPLETED
        procurementOrchestrator.orchestrate(procurementId);

        return toDto(approval);
    }

    /**
     * Rejects a pending procurement decision.
     * Transitions state from WAITING_APPROVAL to REJECTED.
     */
    @Transactional
    public ApprovalResponseDto reject(UUID procurementId, ApprovalActionRequest requestPayload, User approver) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        if (request.getStatus() != ProcurementState.WAITING_APPROVAL) {
            throw new ApprovalException("Cannot reject procurement. Current state is [" + request.getStatus() + "], expected [WAITING_APPROVAL].");
        }

        Approval approval = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("No pending approval found for procurement: " + procurementId));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new ApprovalException("Approval decision has already been reached with status [" + approval.getStatus() + "]. Replay blocked.");
        }

        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setDecidedAt(Instant.now());
        approval.setDecidedBy(approver);
        approval.setComments(requestPayload != null && requestPayload.getComments() != null ? requestPayload.getComments() : "Rejected by manager");

        approvalRepository.save(approval);

        // Transition state: WAITING_APPROVAL -> REJECTED
        stateMachine.handleApprovalDecision(request, false,
                approver != null ? approver.getName() : "MANAGER",
                approval.getComments(),
                Map.of("approvalId", approval.getId().toString()));

        log.info("Procurement [{}] REJECTED by [{}]. Transitioned to REJECTED.", procurementId, approver != null ? approver.getEmail() : "MANAGER");

        return toDto(approval);
    }

    private ApprovalResponseDto toDto(Approval approval) {
        VendorOffer proposed = approval.getProposedOffer();
        return ApprovalResponseDto.builder()
                .approvalId(approval.getId())
                .procurementId(approval.getProcurement() != null ? approval.getProcurement().getId() : null)
                .proposedOfferId(proposed != null ? proposed.getId() : null)
                .proposedProductName(proposed != null && proposed.getProduct() != null ? proposed.getProduct().getName() : null)
                .proposedVendorName(proposed != null && proposed.getVendor() != null ? proposed.getVendor().getName() : null)
                .status(approval.getStatus())
                .requestedAmount(approval.getRequestedAmount())
                .authorizationLimit(approval.getAuthorizationLimit())
                .difference(approval.getDifference())
                .exceptionType(approval.getExceptionType())
                .reason(approval.getReason())
                .explanation(approval.getReason())
                .comments(approval.getComments())
                .requestedAt(approval.getRequestedAt())
                .decidedAt(approval.getDecidedAt())
                .decidedByName(approval.getDecidedBy() != null ? approval.getDecidedBy().getName() : null)
                .build();
    }
}
