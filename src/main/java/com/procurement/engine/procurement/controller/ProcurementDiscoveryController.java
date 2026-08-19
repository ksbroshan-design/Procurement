package com.procurement.engine.procurement.controller;

import com.procurement.engine.common.model.ApiResponse;
import com.procurement.engine.comparison.model.ProcurementComparisonResponse;
import com.procurement.engine.comparison.service.ComparisonService;
import com.procurement.engine.discovery.model.CandidateOfferDto;
import com.procurement.engine.discovery.model.DiscoveryResult;
import com.procurement.engine.discovery.model.RejectionDiagnosticDto;
import com.procurement.engine.discovery.service.DiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Procurement Discovery, Normalized Candidate Products,
 * Comparison, and Rejection Diagnostics.
 */
@RestController
@RequestMapping("/api/procurements")
public class ProcurementDiscoveryController {

    private final DiscoveryService discoveryService;
    private final ComparisonService comparisonService;

    public ProcurementDiscoveryController(DiscoveryService discoveryService,
                                          ComparisonService comparisonService) {
        this.discoveryService = discoveryService;
        this.comparisonService = comparisonService;
    }

    /**
     * POST /api/procurements/{id}/discover
     * Starts product discovery, normalization, and constraint evaluation across all vendor sources.
     */
    @PostMapping("/{id}/discover")
    public ResponseEntity<ApiResponse<DiscoveryResult>> discoverProducts(@PathVariable("id") UUID id) {
        DiscoveryResult result = discoveryService.discoverAndEvaluate(id);
        return ResponseEntity.ok(ApiResponse.success("Discovery process completed", result));
    }

    /**
     * GET /api/procurements/{id}/products
     * Returns candidate products/offers discovered for the procurement.
     */
    @GetMapping("/{id}/products")
    public ResponseEntity<ApiResponse<List<CandidateOfferDto>>> getDiscoveredProducts(@PathVariable("id") UUID id) {
        List<CandidateOfferDto> products = discoveryService.getDiscoveredProducts(id);
        return ResponseEntity.ok(ApiResponse.success("Discovered candidate products retrieved", products));
    }

    /**
     * GET /api/procurements/{id}/comparison
     * Returns normalized side-by-side comparison data for eligible candidate offers.
     */
    @GetMapping("/{id}/comparison")
    public ResponseEntity<ApiResponse<ProcurementComparisonResponse>> getComparison(@PathVariable("id") UUID id) {
        ProcurementComparisonResponse comparison = comparisonService.compareCandidates(id);
        return ResponseEntity.ok(ApiResponse.success("Procurement comparison retrieved", comparison));
    }

    /**
     * GET /api/procurements/{id}/rejections
     * Returns rejected candidate products with explicit constraint failure diagnostics.
     */
    @GetMapping("/{id}/rejections")
    public ResponseEntity<ApiResponse<List<RejectionDiagnosticDto>>> getRejections(@PathVariable("id") UUID id) {
        List<RejectionDiagnosticDto> rejections = discoveryService.getRejections(id);
        return ResponseEntity.ok(ApiResponse.success("Rejected products and diagnostics retrieved", rejections));
    }
}
