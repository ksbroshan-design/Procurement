package com.procurement.engine.revalidation.service;

import com.procurement.engine.approval.entity.Approval;
import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.repository.ApprovalRepository;
import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.config.EngineProperties;
import com.procurement.engine.constraint.model.ProductConstraintEvaluation;
import com.procurement.engine.constraint.service.ConstraintService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.repository.VendorOfferRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.revalidation.model.RevalidationCheckDto;
import com.procurement.engine.revalidation.model.RevalidationResultDto;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.statemachine.ProcurementStateMachine;
import com.procurement.engine.vendor.entity.Vendor;
import com.procurement.engine.vendor.entity.VendorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Pre-purchase Revalidation Service.
 * <p>
 * Performs comprehensive pre-purchase verification of vendor status, inventory stock,
 * price stability, constraint eligibility, and authorization boundaries.
 */
@Service
public class RevalidationService {

    private static final Logger log = LoggerFactory.getLogger(RevalidationService.class);

    private final ConstraintService constraintService;
    private final ProcurementStateMachine stateMachine;
    private final EngineProperties engineProperties;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final ProductRepository productRepository;
    private final VendorOfferRepository vendorOfferRepository;
    private final ApprovalRepository approvalRepository;

    public RevalidationService(ConstraintService constraintService,
                               ProcurementStateMachine stateMachine,
                               EngineProperties engineProperties,
                               ProcurementRequestRepository procurementRequestRepository,
                               ProductRepository productRepository,
                               VendorOfferRepository vendorOfferRepository,
                               ApprovalRepository approvalRepository) {
        this.constraintService = constraintService;
        this.stateMachine = stateMachine;
        this.engineProperties = engineProperties;
        this.procurementRequestRepository = procurementRequestRepository;
        this.productRepository = productRepository;
        this.vendorOfferRepository = vendorOfferRepository;
        this.approvalRepository = approvalRepository;
    }

    /**
     * Executes pre-purchase revalidation for a procurement request.
     */
    @Transactional
    public RevalidationResultDto revalidate(UUID procurementId) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        int maxRetries = engineProperties.getRevalidation().getMaxRetryAttempts();
        int attempts = request.getRevalidationAttempts();

        // 1. Resolve Server-Authoritative Selected / Approved Offer
        VendorOffer offer = resolveTargetOffer(request);
        if (offer == null) {
            log.warn("Revalidation failed for procurement [{}]: No selected/approved offer found.", procurementId);
            return RevalidationResultDto.builder()
                    .procurementId(procurementId)
                    .status("INVALID")
                    .valid(false)
                    .revalidationAttempts(attempts)
                    .maxRetryAttempts(maxRetries)
                    .message("No selected or approved offer found for procurement request.")
                    .nextState(request.getStatus().name())
                    .build();
        }

        Product product = offer.getProduct();
        Vendor vendor = offer.getVendor();
        int quantity = request.getQuantity();

        List<RevalidationCheckDto> checks = new ArrayList<>();
        boolean allPassed = true;

        // Check 1: Vendor Active Status
        if (vendor != null && vendor.getStatus() == VendorStatus.ACTIVE) {
            checks.add(RevalidationCheckDto.pass("VENDOR_STATUS", "ACTIVE", vendor.getStatus().name(), "Vendor is active and verified."));
        } else {
            allPassed = false;
            String actual = vendor != null && vendor.getStatus() != null ? vendor.getStatus().name() : "UNKNOWN";
            checks.add(RevalidationCheckDto.fail("VENDOR_STATUS", "ACTIVE", actual, "Vendor is no longer active: " + actual));
        }

        // Check 2: Inventory Availability & Quantity
        int availableStock = product != null ? product.getAvailableQuantity() : 0;
        boolean isAvailable = product != null && product.isAvailability() && availableStock >= quantity;
        if (isAvailable) {
            checks.add(RevalidationCheckDto.pass("INVENTORY", ">=" + quantity, String.valueOf(availableStock),
                    String.format("Sufficient inventory confirmed: %d units available (required: %d).", availableStock, quantity)));
        } else {
            allPassed = false;
            checks.add(RevalidationCheckDto.fail("INVENTORY", ">=" + quantity, String.valueOf(availableStock),
                    String.format("Insufficient stock: %d units available (required: %d).", availableStock, quantity)));
        }

        // Check 3: Price Stability (Approved Price == Current Catalog Price)
        BigDecimal approvedUnitPrice = offer.getOriginalPrice() != null ? offer.getOriginalPrice().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal currentCatalogPrice = product != null && product.getPrice() != null ? product.getPrice().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (approvedUnitPrice.compareTo(currentCatalogPrice) == 0) {
            checks.add(RevalidationCheckDto.pass("PRICE_STABILITY", "₹" + approvedUnitPrice, "₹" + currentCatalogPrice, "Price confirmed stable with vendor catalog."));
        } else {
            allPassed = false;
            checks.add(RevalidationCheckDto.fail("PRICE_STABILITY", "₹" + approvedUnitPrice, "₹" + currentCatalogPrice,
                    String.format("Price has changed from approved ₹%s to current catalog ₹%s. Offer is stale.", approvedUnitPrice, currentCatalogPrice)));
        }

        // Check 4: Mandatory Procurement Constraints
        if (product != null) {
            ProductConstraintEvaluation constraintEval = constraintService.evaluateProduct(product, request.getConstraints());
            if (constraintEval.isEligible()) {
                checks.add(RevalidationCheckDto.pass("HARD_CONSTRAINTS", "ALL_PASS", "ALL_PASS", "All mandatory hard constraints satisfied."));
            } else {
                allPassed = false;
                checks.add(RevalidationCheckDto.fail("HARD_CONSTRAINTS", "ALL_PASS", "VIOLATED", "Hard constraints violated: " + constraintEval.getSummary()));
            }
        }

        // Check 5: Delivery Timeline Constraint
        int deliveryDays = product != null ? product.getDeliveryDays() : offer.getDeliveryDays();
        checks.add(RevalidationCheckDto.pass("DELIVERY_TIMELINE", "<=" + deliveryDays + " days", deliveryDays + " days", "Delivery timeline confirmed."));

        // Check 6: Authorization & Approved Budget Bounds
        BigDecimal currentTotal = currentCatalogPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal effectiveLimit = resolveAuthorizedLimit(request);
        if (currentTotal.compareTo(effectiveLimit) <= 0) {
            checks.add(RevalidationCheckDto.pass("AUTHORIZATION_BOUNDS", "<=₹" + effectiveLimit, "₹" + currentTotal, "Total purchase amount is covered within authorized limits."));
        } else {
            allPassed = false;
            checks.add(RevalidationCheckDto.fail("AUTHORIZATION_BOUNDS", "<=₹" + effectiveLimit, "₹" + currentTotal,
                    String.format("Current purchase amount ₹%s exceeds authorized limit ₹%s.", currentTotal, effectiveLimit)));
        }

        // 2. Drive State Machine Transitions
        String outcomeStatus;
        String outcomeMessage;
        String nextState;

        if (allPassed) {
            outcomeStatus = "VALID";
            outcomeMessage = "Pre-purchase revalidation passed successfully. All checks verified.";

            // Transition: REVALIDATING -> PURCHASING
            stateMachine.handleRevalidationOutcome(request, true, "REVALIDATION_SERVICE",
                    "Pre-purchase revalidation passed",
                    Map.of("offerId", offer.getId().toString(), "checksCount", checks.size()));

            nextState = ProcurementState.PURCHASING.name();
            log.info("Procurement [{}] revalidation SUCCESS. Transitioned to PURCHASING.", procurementId);
        } else {
            outcomeStatus = "STALE";
            outcomeMessage = "Pre-purchase revalidation failed. Offer conditions changed or become stale.";

            // Increment attempt and transition to SEARCHING (retry) or WAITING_USER (exhausted)
            stateMachine.handleRevalidationOutcome(request, false, "REVALIDATION_SERVICE",
                    "Offer stale/conditions changed",
                    Map.of("offerId", offer.getId().toString(), "failedChecks", checks.stream().filter(c -> !c.isPassed()).count()));

            nextState = request.getStatus().name();
            log.warn("Procurement [{}] revalidation FAILED (attempt {}/{}). Next state: [{}].",
                    procurementId, request.getRevalidationAttempts(), maxRetries, nextState);
        }

        return RevalidationResultDto.builder()
                .procurementId(procurementId)
                .offerId(offer.getId())
                .productName(product != null ? product.getName() : "Product")
                .vendorName(vendor != null ? vendor.getName() : "Vendor")
                .status(outcomeStatus)
                .valid(allPassed)
                .revalidationAttempts(request.getRevalidationAttempts())
                .maxRetryAttempts(maxRetries)
                .checks(checks)
                .message(outcomeMessage)
                .nextState(nextState)
                .build();
    }

    private VendorOffer resolveTargetOffer(ProcurementRequest request) {
        if (request.getSelectedOffer() != null) {
            return request.getSelectedOffer();
        }

        // Check if an approved offer exists in Approval entity
        Optional<Approval> approvalOpt = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(request.getId());
        if (approvalOpt.isPresent() && approvalOpt.get().getStatus() == ApprovalStatus.APPROVED) {
            VendorOffer approvedOffer = approvalOpt.get().getProposedOffer();
            if (approvedOffer != null) {
                request.setSelectedOffer(approvedOffer);
                request.setSelectedProduct(approvedOffer.getProduct());
                procurementRequestRepository.save(request);
                return approvedOffer;
            }
        }

        // Check if any recommended offer exists
        List<VendorOffer> offers = vendorOfferRepository.findByProcurementId(request.getId());
        for (VendorOffer o : offers) {
            if (o.getProduct() != null && request.getSelectedProduct() != null
                    && o.getProduct().getId().equals(request.getSelectedProduct().getId())) {
                request.setSelectedOffer(o);
                procurementRequestRepository.save(request);
                return o;
            }
        }

        return offers.isEmpty() ? null : offers.get(0);
    }

    private BigDecimal resolveAuthorizedLimit(ProcurementRequest request) {
        // If an approval was granted for an exception amount, that amount is the approved limit
        Optional<Approval> approvalOpt = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(request.getId());
        if (approvalOpt.isPresent() && approvalOpt.get().getStatus() == ApprovalStatus.APPROVED) {
            return approvalOpt.get().getRequestedAmount();
        }

        if (request.getAuthorizationLimit() != null && request.getAuthorizationLimit().compareTo(BigDecimal.ZERO) > 0) {
            return request.getAuthorizationLimit();
        }

        if (request.getUser() != null && request.getUser().getAuthorizationLimit() != null) {
            return request.getUser().getAuthorizationLimit();
        }

        return new BigDecimal("500000.00");
    }
}
