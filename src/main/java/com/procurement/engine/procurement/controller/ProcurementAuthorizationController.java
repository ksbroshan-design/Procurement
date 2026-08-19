package com.procurement.engine.procurement.controller;

import com.procurement.engine.approval.service.ApprovalService;
import com.procurement.engine.authorization.model.ApprovalActionRequest;
import com.procurement.engine.authorization.model.ApprovalResponseDto;
import com.procurement.engine.authorization.model.AuthorizationDecisionDto;
import com.procurement.engine.authorization.service.AuthorizationService;
import com.procurement.engine.common.model.ApiResponse;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for Procurement Authorization, Escalations, and Human Approvals/Rejections.
 */
@RestController
@RequestMapping("/api/procurements")
public class ProcurementAuthorizationController {

    private final AuthorizationService authorizationService;
    private final ApprovalService approvalService;
    private final UserRepository userRepository;
    private final ProcurementRequestRepository procurementRequestRepository;

    public ProcurementAuthorizationController(AuthorizationService authorizationService,
                                             ApprovalService approvalService,
                                             UserRepository userRepository,
                                             ProcurementRequestRepository procurementRequestRepository) {
        this.authorizationService = authorizationService;
        this.approvalService = approvalService;
        this.userRepository = userRepository;
        this.procurementRequestRepository = procurementRequestRepository;
    }

    /**
     * POST /api/procurements/{id}/authorize
     * Evaluates authorization limits against recommended procurement choice.
     */
    @PostMapping("/{id}/authorize")
    public ResponseEntity<ApiResponse<AuthorizationDecisionDto>> authorize(@PathVariable("id") UUID id) {
        AuthorizationDecisionDto decision = authorizationService.checkAuthorization(id);
        return ResponseEntity.ok(ApiResponse.success("Authorization evaluation completed", decision));
    }

    /**
     * GET /api/procurements/{id}/approval
     * Returns pending human-in-the-loop approval record and financial explanation.
     */
    @GetMapping("/{id}/approval")
    public ResponseEntity<ApiResponse<ApprovalResponseDto>> getApproval(@PathVariable("id") UUID id) {
        ApprovalResponseDto approval = approvalService.getApproval(id);
        return ResponseEntity.ok(ApiResponse.success("Approval record retrieved", approval));
    }

    /**
     * POST /api/procurements/{id}/approval/approve
     * Approves pending procurement decision and advances state to REVALIDATING.
     */
    @PostMapping("/{id}/approval/approve")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ApprovalResponseDto>> approve(@PathVariable("id") UUID id,
                                                                   @RequestBody(required = false) ApprovalActionRequest requestPayload,
                                                                   Authentication authentication) {
        User approver = resolveAuthenticatedUser(authentication, id);
        ApprovalResponseDto approval = approvalService.approve(id, requestPayload, approver);
        return ResponseEntity.ok(ApiResponse.success("Procurement approved successfully. Proceeding to revalidation.", approval));
    }

    /**
     * POST /api/procurements/{id}/approval/reject
     * Rejects pending procurement decision and advances state to REJECTED.
     */
    @PostMapping("/{id}/approval/reject")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ApprovalResponseDto>> reject(@PathVariable("id") UUID id,
                                                                  @RequestBody(required = false) ApprovalActionRequest requestPayload,
                                                                  Authentication authentication) {
        User approver = resolveAuthenticatedUser(authentication, id);
        ApprovalResponseDto approval = approvalService.reject(id, requestPayload, approver);
        return ResponseEntity.ok(ApiResponse.success("Procurement rejected.", approval));
    }

    private User resolveAuthenticatedUser(Authentication authentication, UUID procurementId) {
        if (authentication != null && authentication.getName() != null) {
            Optional<User> userOpt = userRepository.findByEmail(authentication.getName());
            if (userOpt.isPresent()) {
                return userOpt.get();
            }
        }

        // Default to active manager user
        return userRepository.findByEmail("manager@procurement.com").orElse(null);
    }
}
