package com.procurement.engine.procurement.controller;

import com.procurement.engine.common.model.ApiResponse;
import com.procurement.engine.ranking.model.ProcurementRankingResponse;
import com.procurement.engine.ranking.service.RankingService;
import com.procurement.engine.recommendation.model.RecommendationResponse;
import com.procurement.engine.recommendation.service.RecommendationService;
import com.procurement.engine.tco.model.TcoBreakdownDto;
import com.procurement.engine.tco.service.TcoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Total Cost of Ownership (TCO), Multi-Dimensional Ranking,
 * False Economy Diagnostics, and Explainable Recommendations.
 */
@RestController
@RequestMapping("/api/procurements")
public class ProcurementIntelligenceController {

    private final TcoService tcoService;
    private final RankingService rankingService;
    private final RecommendationService recommendationService;

    public ProcurementIntelligenceController(TcoService tcoService,
                                             RankingService rankingService,
                                             RecommendationService recommendationService) {
        this.tcoService = tcoService;
        this.rankingService = rankingService;
        this.recommendationService = recommendationService;
    }

    /**
     * POST /api/procurements/{id}/analyze-tco
     * Triggers deterministic TCO calculations and false economy analysis.
     */
    @PostMapping("/{id}/analyze-tco")
    public ResponseEntity<ApiResponse<TcoService.TcoAnalysisResult>> analyzeTco(@PathVariable("id") UUID id) {
        TcoService.TcoAnalysisResult result = tcoService.calculateTcoForProcurement(id);
        return ResponseEntity.ok(ApiResponse.success("TCO analysis and false economy detection completed", result));
    }

    /**
     * GET /api/procurements/{id}/tco
     * Returns detailed TCO breakdowns for all candidate offers.
     */
    @GetMapping("/{id}/tco")
    public ResponseEntity<ApiResponse<List<TcoBreakdownDto>>> getTcoBreakdowns(@PathVariable("id") UUID id) {
        TcoService.TcoAnalysisResult result = tcoService.calculateTcoForProcurement(id);
        return ResponseEntity.ok(ApiResponse.success("TCO breakdowns retrieved", result.allBreakdowns()));
    }

    /**
     * GET /api/procurements/{id}/ranking
     * Returns multi-dimensional ranking across eligible and exception pools.
     */
    @GetMapping("/{id}/ranking")
    public ResponseEntity<ApiResponse<ProcurementRankingResponse>> getRanking(@PathVariable("id") UUID id) {
        ProcurementRankingResponse ranking = rankingService.rankOffers(id);
        return ResponseEntity.ok(ApiResponse.success("Multi-dimensional offer ranking retrieved", ranking));
    }

    /**
     * GET /api/procurements/{id}/recommendation
     * Returns two-tier explainable recommendation.
     */
    @GetMapping("/{id}/recommendation")
    public ResponseEntity<ApiResponse<RecommendationResponse>> getRecommendation(@PathVariable("id") UUID id) {
        RecommendationResponse recommendation = recommendationService.generateRecommendation(id);
        return ResponseEntity.ok(ApiResponse.success("Procurement recommendation generated", recommendation));
    }
}
