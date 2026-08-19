package com.procurement.engine.discovery.service;

import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.constraint.model.ProductConstraintEvaluation;
import com.procurement.engine.constraint.model.SingleConstraintResult;
import com.procurement.engine.constraint.service.ConstraintService;
import com.procurement.engine.discovery.model.*;
import com.procurement.engine.discovery.source.ProductDiscoverySource;
import com.procurement.engine.normalization.model.NormalizedProductCandidate;
import com.procurement.engine.normalization.service.ProductNormalizationService;
import com.procurement.engine.procurement.entity.OfferStatus;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.repository.VendorOfferRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.statemachine.ProcurementStateMachine;
import com.procurement.engine.vendor.entity.Vendor;
import com.procurement.engine.vendor.entity.VendorStatus;
import com.procurement.engine.vendor.repository.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service orchestrating multi-source product discovery, normalization, duplicate handling,
 * and constraint evaluation for procurement requests.
 */
@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final List<ProductDiscoverySource> discoverySources;
    private final ProductNormalizationService normalizationService;
    private final ConstraintService constraintService;
    private final ProcurementStateMachine stateMachine;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final VendorOfferRepository vendorOfferRepository;

    public DiscoveryService(List<ProductDiscoverySource> discoverySources,
                            ProductNormalizationService normalizationService,
                            ConstraintService constraintService,
                            ProcurementStateMachine stateMachine,
                            ProcurementRequestRepository procurementRequestRepository,
                            ProductRepository productRepository,
                            VendorRepository vendorRepository,
                            VendorOfferRepository vendorOfferRepository) {
        this.discoverySources = discoverySources != null ? discoverySources : Collections.emptyList();
        this.normalizationService = normalizationService;
        this.constraintService = constraintService;
        this.stateMachine = stateMachine;
        this.procurementRequestRepository = procurementRequestRepository;
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.vendorOfferRepository = vendorOfferRepository;
    }

    /**
     * Executes product discovery and evaluation across all enabled vendor sources.
     */
    @Transactional
    public DiscoveryResult discoverAndEvaluate(UUID procurementId) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        // 1. Advance state to SEARCHING if needed
        ensureStateIsSearching(request);

        String category = request.getCategory();
        int quantity = request.getQuantity();

        List<String> sourcesQueried = new ArrayList<>();
        List<RawProductCandidate> rawCandidates = new ArrayList<>();
        List<SourceFailureDto> sourceFailures = new ArrayList<>();

        // 2. Query all discovery sources
        for (ProductDiscoverySource source : discoverySources) {
            if (!source.isEnabled()) {
                continue;
            }
            sourcesQueried.add(source.getSourceName());
            try {
                DiscoverySourceResult result = source.discover(category, quantity);
                if (result != null && result.isSuccess()) {
                    rawCandidates.addAll(result.getCandidates());
                } else {
                    String error = (result != null && result.getErrorMessage() != null)
                            ? result.getErrorMessage()
                            : "Unknown discovery error from source: " + source.getSourceName();
                    log.warn("Discovery source [{}] reported failure: {}", source.getSourceName(), error);
                    sourceFailures.add(SourceFailureDto.of(source.getSourceName(), error));
                }
            } catch (Exception ex) {
                log.error("Exception during discovery from source [{}]: {}", source.getSourceName(), ex.getMessage(), ex);
                sourceFailures.add(SourceFailureDto.of(source.getSourceName(), "Exception: " + ex.getMessage()));
            }
        }

        // 3. Handle zero raw candidates
        if (rawCandidates.isEmpty()) {
            log.info("No raw candidates discovered for category [{}] in procurement [{}]", category, procurementId);
            return new DiscoveryResult(
                    procurementId,
                    category,
                    sourcesQueried,
                    0, 0, 0, 0,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    sourceFailures,
                    "NO_DISCOVERY_RESULTS",
                    "No candidate products discovered from any vendor source for category '" + category + "'."
            );
        }

        // 4. Product Normalization
        List<NormalizedProductCandidate> normalizedCandidates = normalizationService.normalizeAll(rawCandidates);

        // 5. Deduplicate and create / link Product & VendorOffer records
        List<CandidateOfferDto> eligibleOffers = new ArrayList<>();
        List<RejectionDiagnosticDto> rejectedOffers = new ArrayList<>();

        List<ProcurementConstraint> constraints = request.getConstraints();

        for (NormalizedProductCandidate normalized : normalizedCandidates) {
            Product product = resolveOrCreateProduct(normalized);
            Vendor vendor = resolveVendor(normalized);

            if (vendor != null && vendor.getStatus() != VendorStatus.ACTIVE) {
                log.info("Skipping product offer from inactive/suspended vendor: {}", vendor.getName());
                continue;
            }

            VendorOffer offer = createOrUpdateVendorOffer(request, vendor, product, normalized);

            // 6. Evaluate with Phase 2 Constraint Engine
            ProductConstraintEvaluation evaluation = constraintService.evaluateProduct(product, constraints);

            if (evaluation.isEligible()) {
                CandidateOfferDto offerDto = CandidateOfferDto.builder()
                        .offerId(offer.getId())
                        .productId(product.getId())
                        .productName(product.getName())
                        .brand(product.getBrand())
                        .model(product.getModel())
                        .category(product.getCategory())
                        .vendorId(vendor != null ? vendor.getId() : null)
                        .vendorName(vendor != null ? vendor.getName() : normalized.getVendorName())
                        .sourceName(normalized.getSourceName())
                        .price(offer.getOriginalPrice())
                        .currency(product.getCurrency())
                        .deliveryDays(offer.getDeliveryDays())
                        .availableQuantity(offer.getAvailableQuantity())
                        .warrantyYears(offer.getWarrantyYears())
                        .warrantyType(product.getWarrantyType())
                        .sellerRating(product.getSellerRating())
                        .reliabilityScore(product.getReliabilityScore())
                        .returnPolicy(vendor != null ? vendor.getReturnPolicy() : normalized.getReturnPolicy())
                        .specifications(product.getSpecifications())
                        .eligible(true)
                        .softPreferencePenalty(evaluation.getTotalPenalty())
                        .evaluationSummary(evaluation.getSummary())
                        .build();
                eligibleOffers.add(offerDto);
            } else {
                List<RejectionDiagnosticDto.FailedConstraintDetail> failedDetails = new ArrayList<>();
                for (SingleConstraintResult cr : evaluation.getConstraintResults()) {
                    if (!cr.isPassed() && cr.isMandatory()) {
                        failedDetails.add(new RejectionDiagnosticDto.FailedConstraintDetail(
                                cr.getAttribute(),
                                cr.getOperator(),
                                cr.getExpectedValue(),
                                cr.getActualValue(),
                                cr.getReason()
                        ));
                    }
                }

                RejectionDiagnosticDto rejectionDto = new RejectionDiagnosticDto(
                        product.getId(),
                        offer.getId(),
                        product.getName(),
                        vendor != null ? vendor.getName() : normalized.getVendorName(),
                        product.getCategory(),
                        offer.getOriginalPrice(),
                        failedDetails
                );
                rejectedOffers.add(rejectionDto);
            }
        }

        // 7. Determine status and transition state to EVALUATING if candidates were processed
        String outcomeStatus;
        String message;

        if (eligibleOffers.isEmpty()) {
            outcomeStatus = "NO_ELIGIBLE_PRODUCTS";
            message = "Candidate products were discovered, but none satisfied mandatory hard constraints.";
            log.info("Procurement [{}] produced 0 eligible offers and {} rejected offers.", procurementId, rejectedOffers.size());
        } else {
            outcomeStatus = "SUCCESS";
            message = String.format("Discovery and constraint evaluation completed: %d eligible, %d rejected.",
                    eligibleOffers.size(), rejectedOffers.size());
            log.info("Procurement [{}] successfully evaluated {} eligible offers.", procurementId, eligibleOffers.size());

            // Transition state to EVALUATING
            if (request.getStatus() == ProcurementState.SEARCHING) {
                stateMachine.transition(request, ProcurementState.EVALUATING, "DISCOVERY_SERVICE",
                        "Discovered and evaluated candidates",
                        Map.of("eligibleOffers", eligibleOffers.size(), "rejectedOffers", rejectedOffers.size()));
            }
        }

        return new DiscoveryResult(
                procurementId,
                category,
                sourcesQueried,
                rawCandidates.size(),
                normalizedCandidates.size(),
                eligibleOffers.size(),
                rejectedOffers.size(),
                eligibleOffers,
                rejectedOffers,
                sourceFailures,
                outcomeStatus,
                message
        );
    }

    /**
     * Returns candidate offers for a procurement.
     */
    @Transactional(readOnly = true)
    public List<CandidateOfferDto> getDiscoveredProducts(UUID procurementId) {
        DiscoveryResult result = discoverAndEvaluate(procurementId);
        return result.getEligibleOffers();
    }

    /**
     * Returns rejected candidates and failure diagnostics for a procurement.
     */
    @Transactional(readOnly = true)
    public List<RejectionDiagnosticDto> getRejections(UUID procurementId) {
        DiscoveryResult result = discoverAndEvaluate(procurementId);
        return result.getRejectedOffers();
    }

    private void ensureStateIsSearching(ProcurementRequest request) {
        if (request.getStatus() == ProcurementState.SUBMITTED) {
            stateMachine.transition(request, ProcurementState.VALIDATING, "DISCOVERY_SERVICE", "Validating request", Map.of());
            stateMachine.transition(request, ProcurementState.SEARCHING, "DISCOVERY_SERVICE", "Starting discovery search", Map.of());
        } else if (request.getStatus() == ProcurementState.VALIDATING) {
            stateMachine.transition(request, ProcurementState.SEARCHING, "DISCOVERY_SERVICE", "Starting discovery search", Map.of());
        }
    }

    private Product resolveOrCreateProduct(NormalizedProductCandidate normalized) {
        if (normalized.getRawId() != null) {
            try {
                UUID rawUuid = UUID.fromString(normalized.getRawId());
                Optional<Product> existingById = productRepository.findById(rawUuid);
                if (existingById.isPresent()) {
                    return existingById.get();
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // Try lookup by brand and model
        if (normalized.getBrand() != null && normalized.getModel() != null) {
            List<Product> matches = productRepository.findByCategoryIgnoreCase(normalized.getCategory());
            for (Product p : matches) {
                if (normalized.getBrand().equalsIgnoreCase(p.getBrand()) && normalized.getModel().equalsIgnoreCase(p.getModel())) {
                    return p;
                }
            }
        }

        Vendor vendor = resolveVendor(normalized);

        Product newProduct = Product.builder()
                .vendor(vendor)
                .name(normalized.getName())
                .category(normalized.getCategory())
                .brand(normalized.getBrand())
                .model(normalized.getModel())
                .price(normalized.getPrice())
                .currency(normalized.getCurrency())
                .availability(normalized.isAvailability())
                .availableQuantity(normalized.getAvailableQuantity())
                .deliveryDays(normalized.getDeliveryDays())
                .sellerRating(normalized.getSellerRating())
                .reliabilityScore(normalized.getReliabilityScore())
                .warrantyDuration(normalized.getWarrantyDuration())
                .warrantyType(normalized.getWarrantyType())
                .returnWindow(30)
                .specifications(normalized.getSpecifications())
                .build();

        return productRepository.save(newProduct);
    }

    private Vendor resolveVendor(NormalizedProductCandidate normalized) {
        if (normalized.getVendorId() != null) {
            Optional<Vendor> vOpt = vendorRepository.findById(normalized.getVendorId());
            if (vOpt.isPresent()) {
                return vOpt.get();
            }
        }
        if (normalized.getVendorName() != null) {
            Optional<Vendor> vOpt = vendorRepository.findByName(normalized.getVendorName());
            if (vOpt.isPresent()) {
                return vOpt.get();
            }
        }
        return null;
    }

    private VendorOffer createOrUpdateVendorOffer(ProcurementRequest request,
                                                 Vendor vendor,
                                                 Product product,
                                                 NormalizedProductCandidate normalized) {
        // Check if an offer already exists for this procurement, vendor, and product
        List<VendorOffer> existingOffers = vendorOfferRepository.findByProcurementId(request.getId());
        for (VendorOffer existing : existingOffers) {
            if (existing.getProduct() != null && existing.getProduct().getId().equals(product.getId())
                    && existing.getVendor() != null && vendor != null && existing.getVendor().getId().equals(vendor.getId())) {
                return existing;
            }
        }

        VendorOffer offer = VendorOffer.builder()
                .procurement(request)
                .vendor(vendor)
                .product(product)
                .originalPrice(normalized.getPrice())
                .negotiatedPrice(null)
                .deliveryDays(normalized.getDeliveryDays())
                .availableQuantity(normalized.getAvailableQuantity())
                .warrantyYears(normalized.getWarrantyDuration())
                .tco(null)
                .status(OfferStatus.EVALUATING)
                .build();

        request.addOffer(offer);
        return vendorOfferRepository.save(offer);
    }
}
