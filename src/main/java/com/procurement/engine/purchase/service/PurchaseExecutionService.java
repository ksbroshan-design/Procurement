package com.procurement.engine.purchase.service;

import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.procurement.entity.OfferStatus;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.repository.VendorOfferRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.purchase.entity.PurchaseOrder;
import com.procurement.engine.purchase.entity.PurchaseOrderStatus;
import com.procurement.engine.purchase.model.PurchaseExecutionResultDto;
import com.procurement.engine.purchase.model.PurchaseOrderDto;
import com.procurement.engine.purchase.repository.PurchaseOrderRepository;
import com.procurement.engine.revalidation.model.RevalidationResultDto;
import com.procurement.engine.revalidation.service.RevalidationService;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.statemachine.ProcurementStateMachine;
import com.procurement.engine.vendor.entity.Vendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mock Purchase Execution Service.
 * <p>
 * Enforces mandatory prior revalidation, creates confirmed Purchase Orders,
 * updates inventory stock, and transitions the state machine to COMPLETED.
 */
@Service
public class PurchaseExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseExecutionService.class);

    private final RevalidationService revalidationService;
    private final ProcurementStateMachine stateMachine;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final VendorOfferRepository vendorOfferRepository;

    public PurchaseExecutionService(RevalidationService revalidationService,
                                    ProcurementStateMachine stateMachine,
                                    ProcurementRequestRepository procurementRequestRepository,
                                    PurchaseOrderRepository purchaseOrderRepository,
                                    ProductRepository productRepository,
                                    VendorOfferRepository vendorOfferRepository) {
        this.revalidationService = revalidationService;
        this.stateMachine = stateMachine;
        this.procurementRequestRepository = procurementRequestRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productRepository = productRepository;
        this.vendorOfferRepository = vendorOfferRepository;
    }

    /**
     * Executes mock purchase for an approved/revalidated procurement request.
     * Enforces idempotency, stock deduction, and final completion.
     */
    @Transactional
    public PurchaseExecutionResultDto executePurchase(UUID procurementId) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        // 1. Idempotency Check: If already completed with a confirmed PO, return it safely
        Optional<PurchaseOrder> existingPoOpt = purchaseOrderRepository.findTopByProcurementIdOrderByCreatedAtDesc(procurementId);
        if (request.getStatus() == ProcurementState.COMPLETED && existingPoOpt.isPresent()) {
            PurchaseOrder existingPo = existingPoOpt.get();
            log.info("Idempotent purchase request for completed procurement [{}] - returning existing PO [{}]",
                    procurementId, existingPo.getId());
            return toExecutionResult(existingPo, "ALREADY_COMPLETED", "Purchase order already confirmed for this procurement.");
        }

        // 2. Self-Protection: Ensure procurement has successfully revalidated before purchase
        if (request.getStatus() != ProcurementState.PURCHASING) {
            log.info("Procurement [{}] in state [{}] - invoking pre-purchase revalidation", procurementId, request.getStatus());
            RevalidationResultDto revalResult = revalidationService.revalidate(procurementId);

            request = procurementRequestRepository.findById(procurementId).orElseThrow();
            if (!revalResult.isValid() || request.getStatus() != ProcurementState.PURCHASING) {
                log.error("Purchase blocked for procurement [{}]: Revalidation failed with status [{}]", procurementId, revalResult.getStatus());
                throw new IllegalStateException("Cannot execute purchase. Pre-purchase revalidation failed: " + revalResult.getMessage());
            }
        }

        // 3. Resolve Server-Authoritative Offer & Product
        VendorOffer selectedOffer = request.getSelectedOffer();
        if (selectedOffer == null) {
            throw new IllegalStateException("Cannot execute purchase: No selected offer associated with procurement [" + procurementId + "].");
        }

        Product product = selectedOffer.getProduct();
        Vendor vendor = selectedOffer.getVendor();
        int quantity = request.getQuantity();

        if (product == null || vendor == null) {
            throw new IllegalStateException("Cannot execute purchase: Incomplete product/vendor reference on selected offer.");
        }

        BigDecimal unitPrice = selectedOffer.getOriginalPrice() != null ? selectedOffer.getOriginalPrice() : product.getPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        // 4. Inventory Deduction & Offer Status Update
        int currentStock = product.getAvailableQuantity();
        if (currentStock < quantity) {
            throw new IllegalStateException(String.format("Insufficient inventory for purchase. Required: %d, Available: %d", quantity, currentStock));
        }

        product.setAvailableQuantity(currentStock - quantity);
        if (product.getAvailableQuantity() == 0) {
            product.setAvailability(false);
        }
        productRepository.save(product);

        if (selectedOffer.getAvailableQuantity() >= quantity) {
            selectedOffer.setAvailableQuantity(selectedOffer.getAvailableQuantity() - quantity);
        }
        selectedOffer.setStatus(OfferStatus.ACCEPTED);
        vendorOfferRepository.save(selectedOffer);

        // 5. Create and Persist Confirmed Purchase Order
        Instant now = Instant.now();
        PurchaseOrder po = PurchaseOrder.builder()
                .procurement(request)
                .vendor(vendor)
                .product(product)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .status(PurchaseOrderStatus.CONFIRMED)
                .confirmedAt(now)
                .build();

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);

        // 6. Transition State Machine: PURCHASING -> COMPLETED
        stateMachine.handlePurchaseOutcome(request, true, "PURCHASE_SERVICE",
                "Purchase order confirmed and executed successfully",
                Map.of("purchaseOrderId", savedPo.getId().toString(), "totalAmount", totalAmount.toString()));

        log.info("Procurement [{}] successfully PURCHASED. PO [{}] generated. State: COMPLETED.", procurementId, savedPo.getId());

        return toExecutionResult(savedPo, "CONFIRMED", "Purchase order successfully placed and confirmed with vendor.");
    }

    /**
     * Retrieves the confirmed purchase order for a procurement request.
     */
    @Transactional(readOnly = true)
    public PurchaseOrderDto getPurchaseOrder(UUID procurementId) {
        PurchaseOrder po = purchaseOrderRepository.findTopByProcurementIdOrderByCreatedAtDesc(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("No purchase order found for procurement: " + procurementId));

        return toDto(po);
    }

    private PurchaseExecutionResultDto toExecutionResult(PurchaseOrder po, String status, String message) {
        return new PurchaseExecutionResultDto(
                po.getId(),
                po.getProcurement() != null ? po.getProcurement().getId() : null,
                po.getVendor() != null ? po.getVendor().getName() : "Vendor",
                po.getProduct() != null ? po.getProduct().getName() : "Product",
                po.getQuantity(),
                po.getUnitPrice(),
                po.getTotalAmount(),
                status,
                po.getConfirmedAt(),
                message,
                po.getProcurement() != null ? po.getProcurement().getStatus().name() : ProcurementState.COMPLETED.name()
        );
    }

    private PurchaseOrderDto toDto(PurchaseOrder po) {
        return new PurchaseOrderDto(
                po.getId(),
                po.getProcurement() != null ? po.getProcurement().getId() : null,
                po.getVendor() != null ? po.getVendor().getId() : null,
                po.getVendor() != null ? po.getVendor().getName() : "Vendor",
                po.getProduct() != null ? po.getProduct().getId() : null,
                po.getProduct() != null ? po.getProduct().getName() : "Product",
                po.getQuantity(),
                po.getUnitPrice(),
                po.getTotalAmount(),
                po.getStatus(),
                po.getCreatedAt(),
                po.getConfirmedAt()
        );
    }
}
