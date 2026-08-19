package com.procurement.engine.procurement.controller;

import com.procurement.engine.common.model.ApiResponse;
import com.procurement.engine.purchase.model.PurchaseExecutionResultDto;
import com.procurement.engine.purchase.model.PurchaseOrderDto;
import com.procurement.engine.purchase.service.PurchaseExecutionService;
import com.procurement.engine.revalidation.model.RevalidationResultDto;
import com.procurement.engine.revalidation.service.RevalidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for Pre-Purchase Revalidation and Mock Purchase Order Execution.
 */
@RestController
@RequestMapping("/api/procurements")
public class ProcurementExecutionController {

    private final RevalidationService revalidationService;
    private final PurchaseExecutionService purchaseExecutionService;

    public ProcurementExecutionController(RevalidationService revalidationService,
                                         PurchaseExecutionService purchaseExecutionService) {
        this.revalidationService = revalidationService;
        this.purchaseExecutionService = purchaseExecutionService;
    }

    /**
     * POST /api/procurements/{id}/revalidate
     * Executes pre-purchase revalidation checks.
     */
    @PostMapping("/{id}/revalidate")
    public ResponseEntity<ApiResponse<RevalidationResultDto>> revalidate(@PathVariable("id") UUID id) {
        RevalidationResultDto result = revalidationService.revalidate(id);
        return ResponseEntity.ok(ApiResponse.success("Pre-purchase revalidation evaluated", result));
    }

    /**
     * GET /api/procurements/{id}/revalidate
     * Retrieves the latest revalidation evaluation for a procurement request.
     */
    @GetMapping("/{id}/revalidate")
    public ResponseEntity<ApiResponse<RevalidationResultDto>> getRevalidation(@PathVariable("id") UUID id) {
        RevalidationResultDto result = revalidationService.revalidate(id);
        return ResponseEntity.ok(ApiResponse.success("Revalidation status retrieved", result));
    }

    /**
     * POST /api/procurements/{id}/purchase
     * Executes mock purchase, creates PurchaseOrder, and advances state to COMPLETED.
     */
    @PostMapping("/{id}/purchase")
    public ResponseEntity<ApiResponse<PurchaseExecutionResultDto>> purchase(@PathVariable("id") UUID id) {
        PurchaseExecutionResultDto result = purchaseExecutionService.executePurchase(id);
        return ResponseEntity.ok(ApiResponse.success("Purchase order executed successfully", result));
    }

    /**
     * GET /api/procurements/{id}/purchase-order
     * Retrieves the confirmed Purchase Order for a completed procurement request.
     */
    @GetMapping("/{id}/purchase-order")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> getPurchaseOrder(@PathVariable("id") UUID id) {
        PurchaseOrderDto po = purchaseExecutionService.getPurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Purchase order retrieved", po));
    }
}
