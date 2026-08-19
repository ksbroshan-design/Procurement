package com.procurement.engine.procurement.controller;

import com.procurement.engine.audit.model.ProcurementAuditResponse;
import com.procurement.engine.audit.service.AuditService;
import com.procurement.engine.common.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for retrieving the chronological procurement audit trail.
 */
@RestController
@RequestMapping("/api/procurements")
public class ProcurementAuditController {

    private final AuditService auditService;

    public ProcurementAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * GET /api/procurements/{id}/audit
     * Returns the complete chronological audit trail for the given procurement request.
     */
    @GetMapping("/{id}/audit")
    public ResponseEntity<ApiResponse<ProcurementAuditResponse>> getAuditTrail(@PathVariable("id") UUID id) {
        ProcurementAuditResponse auditResponse = auditService.getAuditTrail(id);
        return ResponseEntity.ok(ApiResponse.success("Procurement audit trail retrieved", auditResponse));
    }
}
