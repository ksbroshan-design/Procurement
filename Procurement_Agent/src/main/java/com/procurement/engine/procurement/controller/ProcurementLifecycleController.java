package com.procurement.engine.procurement.controller;

import com.procurement.engine.common.model.ApiResponse;
import com.procurement.engine.procurement.model.CreateProcurementRequestDto;
import com.procurement.engine.procurement.model.OrchestrationResultDto;
import com.procurement.engine.procurement.model.ProcurementSummaryDto;
import com.procurement.engine.procurement.service.ProcurementOrchestrator;
import com.procurement.engine.procurement.service.ProcurementService;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for Procurement Request Creation, Retrieval, and End-to-End Orchestration.
 */
@RestController
@RequestMapping("/api/procurements")
public class ProcurementLifecycleController {

    private final ProcurementService procurementService;
    private final ProcurementOrchestrator procurementOrchestrator;
    private final UserRepository userRepository;

    public ProcurementLifecycleController(ProcurementService procurementService,
                                         ProcurementOrchestrator procurementOrchestrator,
                                         UserRepository userRepository) {
        this.procurementService = procurementService;
        this.procurementOrchestrator = procurementOrchestrator;
        this.userRepository = userRepository;
    }

    /**
     * POST /api/procurements
     * Creates a new procurement request from structured JSON intent.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProcurementSummaryDto>> createProcurement(@RequestBody CreateProcurementRequestDto requestDto,
                                                                               Authentication authentication) {
        User user = resolveAuthenticatedUser(authentication);
        ProcurementSummaryDto summary = procurementService.createProcurement(requestDto, user);
        return ResponseEntity.ok(ApiResponse.success("Procurement request created successfully", summary));
    }

    /**
     * GET /api/procurements
     * Retrieves all procurement requests ordered by creation timestamp.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<ProcurementSummaryDto>>> listProcurements() {
        java.util.List<ProcurementSummaryDto> list = procurementService.getAllProcurements();
        return ResponseEntity.ok(ApiResponse.success("Procurement requests retrieved", list));
    }


    /**
     * GET /api/procurements/{id}
     * Retrieves status and summary of a procurement request safely within a transactional boundary.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcurementSummaryDto>> getProcurement(@PathVariable("id") UUID id) {
        ProcurementSummaryDto summary = procurementService.getProcurementSummary(id);
        return ResponseEntity.ok(ApiResponse.success("Procurement request retrieved", summary));
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
