package com.procurement.engine.procurement.controller;

import com.procurement.engine.audit.entity.AuditEventType;
import com.procurement.engine.audit.service.AuditService;
import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.common.model.ApiResponse;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.model.CreateProcurementRequestDto;
import com.procurement.engine.procurement.model.OrchestrationResultDto;
import com.procurement.engine.procurement.model.ProcurementSummaryDto;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.service.ProcurementOrchestrator;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for Procurement Request Creation, Retrieval, and End-to-End Orchestration.
 */
@RestController
@RequestMapping("/api/procurements")
public class ProcurementLifecycleController {

    private final ProcurementRequestRepository procurementRequestRepository;
    private final ProcurementOrchestrator procurementOrchestrator;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ProcurementLifecycleController(ProcurementRequestRepository procurementRequestRepository,
                                         ProcurementOrchestrator procurementOrchestrator,
                                         UserRepository userRepository,
                                         AuditService auditService) {
        this.procurementRequestRepository = procurementRequestRepository;
        this.procurementOrchestrator = procurementOrchestrator;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * POST /api/procurements
     * Creates a new procurement request from structured JSON intent.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProcurementSummaryDto>> createProcurement(@RequestBody CreateProcurementRequestDto requestDto,
                                                                               Authentication authentication) {
        if (requestDto == null || requestDto.getCategory() == null || requestDto.getCategory().isBlank()) {
            throw new IllegalArgumentException("Category is required to create a procurement request.");
        }
        if (requestDto.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        User user = resolveAuthenticatedUser(authentication);

        ProcurementRequest request = ProcurementRequest.builder()
                .user(user)
                .category(requestDto.getCategory())
                .quantity(requestDto.getQuantity())
                .authorizationLimit(requestDto.getAuthorizationLimit() != null ? requestDto.getAuthorizationLimit() : BigDecimal.ZERO)
                .status(ProcurementState.SUBMITTED)
                .build();

        if (requestDto.getConstraints() != null) {
            requestDto.getConstraints().forEach(c -> {
                if (c.getAttribute() != null && !c.getAttribute().isBlank()) {
                    request.addConstraint(ProcurementConstraint.builder()
                            .attribute(c.getAttribute())
                            .operator(c.resolveOperator())
                            .value(c.getValue() != null ? c.getValue() : "")
                            .mandatory(c.isMandatory())
                            .build());
                }
            });
        }

        ProcurementRequest saved = procurementRequestRepository.save(request);

        auditService.record(
                saved.getId(),
                AuditEventType.REQUEST_CREATED,
                ProcurementState.SUBMITTED,
                user != null ? user.getName() : "AI_AGENT",
                "Procurement request created for category: " + saved.getCategory(),
                Map.of("category", saved.getCategory(), "quantity", saved.getQuantity(),
                        "authorizationLimit", saved.getAuthorizationLimit().toString(),
                        "constraintCount", saved.getConstraints().size())
        );

        return ResponseEntity.ok(ApiResponse.success("Procurement request created successfully", toSummaryDto(saved)));
    }

    /**
     * GET /api/procurements/{id}
     * Retrieves status and summary of a procurement request.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcurementSummaryDto>> getProcurement(@PathVariable("id") UUID id) {
        ProcurementRequest request = procurementRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + id));

        return ResponseEntity.ok(ApiResponse.success("Procurement request retrieved", toSummaryDto(request)));
    }

    /**
     * POST /api/procurements/{id}/execute
     * Triggers deterministic end-to-end backend orchestration.
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<ApiResponse<OrchestrationResultDto>> executeProcurement(@PathVariable("id") UUID id) {
        OrchestrationResultDto result = procurementOrchestrator.orchestrate(id);
        return ResponseEntity.ok(ApiResponse.success("Procurement workflow orchestrated", result));
    }

    private ProcurementSummaryDto toSummaryDto(ProcurementRequest r) {
        return new ProcurementSummaryDto(
                r.getId(),
                r.getCategory(),
                r.getQuantity(),
                r.getAuthorizationLimit(),
                r.getStatus(),
                r.getSelectedOffer() != null ? r.getSelectedOffer().getId() : null,
                r.getSelectedProduct() != null ? r.getSelectedProduct().getName() : null,
                r.getSelectedOffer() != null && r.getSelectedOffer().getVendor() != null ? r.getSelectedOffer().getVendor().getName() : null,
                r.getConstraints().size(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private User resolveAuthenticatedUser(Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            Optional<User> u = userRepository.findByEmail(authentication.getName());
            if (u.isPresent()) {
                return u.get();
            }
        }
        return userRepository.findByEmail("manager@procurement.com").orElse(null);
    }
}
