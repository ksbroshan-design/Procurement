package com.procurement.engine.purchase;

import com.procurement.engine.authorization.service.AuthorizationService;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.OfferStatus;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.purchase.entity.PurchaseOrder;
import com.procurement.engine.purchase.entity.PurchaseOrderStatus;
import com.procurement.engine.purchase.model.PurchaseExecutionResultDto;
import com.procurement.engine.purchase.repository.PurchaseOrderRepository;
import com.procurement.engine.purchase.service.PurchaseExecutionService;
import com.procurement.engine.revalidation.service.RevalidationService;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PurchaseExecutionServiceTest {

    @Autowired
    private PurchaseExecutionService purchaseExecutionService;

    @Autowired
    private RevalidationService revalidationService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private User manager;

    @BeforeEach
    void setUp() {
        manager = userRepository.findByEmail("manager@procurement.com").orElseThrow();
    }

    private ProcurementRequest createReadyProcurement() {
        ProcurementRequest req = ProcurementRequest.builder()
                .user(manager)
                .category("TV")
                .quantity(2)
                .authorizationLimit(new BigDecimal("300000.00"))
                .status(ProcurementState.SUBMITTED)
                .build();

        req.addConstraint(ProcurementConstraint.builder()
                .attribute("screenSize")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("55")
                .mandatory(true)
                .build());

        ProcurementRequest saved = procurementRequestRepository.save(req);
        discoveryService.discoverAndEvaluate(saved.getId());
        authorizationService.checkAuthorization(saved.getId());
        return procurementRequestRepository.findById(saved.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Executes purchase, creates confirmed PurchaseOrder, deducts stock, and completes procurement")
    void testSuccessfulPurchaseExecution() {
        ProcurementRequest request = createReadyProcurement();
        VendorOffer selectedOffer = request.getSelectedOffer();
        Product product = selectedOffer.getProduct();
        int initialStock = product.getAvailableQuantity();
        int quantity = request.getQuantity();

        PurchaseExecutionResultDto result = purchaseExecutionService.executePurchase(request.getId());

        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getPurchaseOrderId()).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(quantity);

        // Verify PurchaseOrder in database
        PurchaseOrder po = purchaseOrderRepository.findById(result.getPurchaseOrderId()).orElseThrow();
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.CONFIRMED);
        assertThat(po.getProcurement().getId()).isEqualTo(request.getId());

        // Verify stock deducted
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getAvailableQuantity()).isEqualTo(initialStock - quantity);

        // Verify offer status ACCEPTED
        assertThat(selectedOffer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);

        // Verify procurement state COMPLETED
        ProcurementRequest updatedRequest = procurementRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updatedRequest.getStatus()).isEqualTo(ProcurementState.COMPLETED);
    }

    @Test
    @DisplayName("Idempotency: Repeated purchase requests on COMPLETED procurement return existing PO without duplicate creation")
    void testIdempotentPurchaseExecution() {
        ProcurementRequest request = createReadyProcurement();

        PurchaseExecutionResultDto firstCall = purchaseExecutionService.executePurchase(request.getId());
        assertThat(firstCall.getStatus()).isEqualTo("CONFIRMED");

        List<PurchaseOrder> ordersAfterFirst = purchaseOrderRepository.findByProcurementId(request.getId());
        assertThat(ordersAfterFirst).hasSize(1);

        // Second call must return existing order
        PurchaseExecutionResultDto secondCall = purchaseExecutionService.executePurchase(request.getId());
        assertThat(secondCall.getStatus()).isEqualTo("ALREADY_COMPLETED");
        assertThat(secondCall.getPurchaseOrderId()).isEqualTo(firstCall.getPurchaseOrderId());

        List<PurchaseOrder> ordersAfterSecond = purchaseOrderRepository.findByProcurementId(request.getId());
        assertThat(ordersAfterSecond).hasSize(1);
    }

    @Test
    @DisplayName("Self-Protection: Direct purchase execution fails when offer is stale (e.g. out of stock)")
    void testDirectPurchaseBlockedOnStaleOffer() {
        ProcurementRequest request = createReadyProcurement();
        VendorOffer selectedOffer = request.getSelectedOffer();
        Product product = selectedOffer.getProduct();

        // Make product out of stock
        product.setAvailableQuantity(0);
        product.setAvailability(false);
        productRepository.save(product);

        assertThatThrownBy(() -> purchaseExecutionService.executePurchase(request.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pre-purchase revalidation failed");

        // Assert no purchase order was created
        List<PurchaseOrder> orders = purchaseOrderRepository.findByProcurementId(request.getId());
        assertThat(orders).isEmpty();

        // Assert state is not COMPLETED
        ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isNotEqualTo(ProcurementState.COMPLETED);
    }
}
