package com.procurement.engine.tco.service;

import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.constraint.model.ProductConstraintEvaluation;
import com.procurement.engine.constraint.service.ConstraintService;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.repository.VendorOfferRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.entity.ReliabilityHistory;
import com.procurement.engine.product.repository.ReliabilityHistoryRepository;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.statemachine.ProcurementStateMachine;
import com.procurement.engine.tco.model.FalseEconomyResult;
import com.procurement.engine.tco.model.TcoBreakdownDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service orchestrating TCO calculation, persistence of offer TCO, false economy detection,
 * and state machine progression for procurement requests.
 */
@Service
public class TcoService {

    private static final Logger log = LoggerFactory.getLogger(TcoService.class);

    private final TcoCalculator tcoCalculator;
    private final FalseEconomyDetector falseEconomyDetector;
    private final DiscoveryService discoveryService;
    private final ConstraintService constraintService;
    private final ProcurementStateMachine stateMachine;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final VendorOfferRepository vendorOfferRepository;
    private final ReliabilityHistoryRepository reliabilityHistoryRepository;

    public TcoService(TcoCalculator tcoCalculator,
                      FalseEconomyDetector falseEconomyDetector,
                      DiscoveryService discoveryService,
                      ConstraintService constraintService,
                      ProcurementStateMachine stateMachine,
                      ProcurementRequestRepository procurementRequestRepository,
                      VendorOfferRepository vendorOfferRepository,
                      ReliabilityHistoryRepository reliabilityHistoryRepository) {
        this.tcoCalculator = tcoCalculator;
        this.falseEconomyDetector = falseEconomyDetector;
        this.discoveryService = discoveryService;
        this.constraintService = constraintService;
        this.stateMachine = stateMachine;
        this.procurementRequestRepository = procurementRequestRepository;
        this.vendorOfferRepository = vendorOfferRepository;
        this.reliabilityHistoryRepository = reliabilityHistoryRepository;
    }

    /**
     * Calculates TCO and detects false economies for all candidate offers of a procurement request.
     */
    @Transactional
    public TcoAnalysisResult calculateTcoForProcurement(UUID procurementId) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        // If offers not yet discovered or request is in early state, run discovery first
        List<VendorOffer> offers = vendorOfferRepository.findByProcurementId(procurementId);
        if (offers.isEmpty() || request.getStatus() == ProcurementState.SUBMITTED || request.getStatus() == ProcurementState.VALIDATING || request.getStatus() == ProcurementState.SEARCHING) {
            discoveryService.discoverAndEvaluate(procurementId);
            request = procurementRequestRepository.findById(procurementId).orElseThrow();
            offers = vendorOfferRepository.findByProcurementId(procurementId);
        }

        List<TcoBreakdownDto> allBreakdowns = new ArrayList<>();
        List<TcoBreakdownDto> eligibleBreakdowns = new ArrayList<>();
        List<TcoBreakdownDto> exceptionBreakdowns = new ArrayList<>();

        for (VendorOffer offer : offers) {
            Product product = offer.getProduct();
            if (product == null) {
                continue;
            }

            Optional<ReliabilityHistory> relHistory = reliabilityHistoryRepository.findTopByProductIdOrderByRecordedAtDesc(product.getId());

            TcoBreakdownDto breakdown = tcoCalculator.calculateTco(
                    product, offer, relHistory, request.getQuantity(), null
            );
            allBreakdowns.add(breakdown);

            // Persist calculated total TCO on VendorOffer
            offer.setTco(breakdown.getTotalTco());
            vendorOfferRepository.save(offer);

            // Check hard constraint eligibility
            ProductConstraintEvaluation eval = constraintService.evaluateProduct(product, request.getConstraints());
            if (eval.isEligible()) {
                eligibleBreakdowns.add(breakdown);
            } else {
                exceptionBreakdowns.add(breakdown);
            }
        }

        // Run False Economy Detection
        List<FalseEconomyResult> falseEconomies = falseEconomyDetector.detectFalseEconomies(eligibleBreakdowns, exceptionBreakdowns);

        // Advance state to TCO_ANALYSIS if currently in EVALUATING or SEARCHING
        if (request.getStatus() == ProcurementState.EVALUATING) {
            stateMachine.transition(request, ProcurementState.TCO_ANALYSIS, "TCO_SERVICE",
                    "Completed deterministic TCO calculations and false economy analysis",
                    Map.of("evaluatedOffers", allBreakdowns.size(), "falseEconomiesDetected", falseEconomies.size()));
        }

        log.info("Calculated TCO for {} offers ({} eligible, {} exception) for procurement [{}]. False economies detected: {}",
                allBreakdowns.size(), eligibleBreakdowns.size(), exceptionBreakdowns.size(), procurementId, falseEconomies.size());

        return new TcoAnalysisResult(procurementId, allBreakdowns, eligibleBreakdowns, exceptionBreakdowns, falseEconomies);
    }

    /**
     * DTO containing all TCO analysis outputs for a procurement.
     */
    public record TcoAnalysisResult(
            UUID procurementId,
            List<TcoBreakdownDto> allBreakdowns,
            List<TcoBreakdownDto> eligibleBreakdowns,
            List<TcoBreakdownDto> exceptionBreakdowns,
            List<FalseEconomyResult> falseEconomyResults
    ) {}
}
