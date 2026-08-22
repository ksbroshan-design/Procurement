package com.procurement.engine.procurement;

import com.procurement.engine.approval.entity.Approval;
import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.repository.ApprovalRepository;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.procurement.entity.OfferStatus;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.purchase.entity.PurchaseOrder;
import com.procurement.engine.purchase.entity.PurchaseOrderStatus;
import com.procurement.engine.purchase.repository.PurchaseOrderRepository;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import com.procurement.engine.vendor.entity.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProcurementEntityPersistenceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Test
    @DisplayName("Verify full entity persistence cycle: Request, Constraints, Offers, Approval, and PurchaseOrder")
    void testFullEntityPersistence() {
        User manager = userRepository.findByEmail("manager@procurement.com").orElseThrow();
        List<Product> tvs = productRepository.findByCategoryIgnoreCase("TV");
        assertThat(tvs).isNotEmpty();
        Product selectedProduct = tvs.get(0);
        Vendor vendor = selectedProduct.getVendor();

        // 1. Create ProcurementRequest
        ProcurementRequest request = ProcurementRequest.builder()
                .user(manager)
                .rawBrief("Procure 5 55-inch OLED TVs under 350,000 INR")
                .category("TV")
                .quantity(5)
                .authorizationLimit(new BigDecimal("350000.00"))
                .status(ProcurementState.SUBMITTED)
                .build();

        // 2. Add Constraints
        ProcurementConstraint c1 = ProcurementConstraint.builder()
                .attribute("screenSize")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("55")
                .unit("inch")
                .mandatory(true)
                .weight(BigDecimal.ONE)
                .build();

        ProcurementConstraint c2 = ProcurementConstraint.builder()
                .attribute("panelType")
                .operator(ConstraintOperator.EQUALS)
                .value("OLED")
                .mandatory(false)
                .weight(new BigDecimal("0.80"))
                .build();

        request.addConstraint(c1);
        request.addConstraint(c2);

        // 3. Add Offer
        VendorOffer offer = VendorOffer.builder()
                .vendor(vendor)
                .product(selectedProduct)
                .originalPrice(selectedProduct.getPrice())
                .negotiatedPrice(selectedProduct.getPrice().subtract(new BigDecimal("1000.00")))
                .deliveryDays(selectedProduct.getDeliveryDays())
                .availableQuantity(selectedProduct.getAvailableQuantity())
                .warrantyYears(selectedProduct.getWarrantyDuration())
                .tco(new BigDecimal("280000.00"))
                .status(OfferStatus.RECOMMENDED)
                .build();

        request.addOffer(offer);
        request.setSelectedProduct(selectedProduct);
        request.setSelectedOffer(offer);

        ProcurementRequest saved = procurementRequestRepository.save(request);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getConstraints()).hasSize(2);
        assertThat(saved.getOffers()).hasSize(1);
        assertThat(saved.getVersion()).isNotNull();

        // 4. Create Approval record
        Approval approval = Approval.builder()
                .procurement(saved)
                .requestedAmount(new BigDecimal("460000.00"))
                .authorizationLimit(saved.getAuthorizationLimit())
                .difference(new BigDecimal("110000.00"))
                .reason("Best TCO OLED option exceeds initial limit")
                .status(ApprovalStatus.PENDING)
                .build();

        Approval savedApproval = approvalRepository.save(approval);
        assertThat(savedApproval.getId()).isNotNull();
        assertThat(savedApproval.getStatus()).isEqualTo(ApprovalStatus.PENDING);

        // 5. Create Mock PurchaseOrder
        PurchaseOrder po = PurchaseOrder.builder()
                .procurement(saved)
                .vendor(vendor)
                .product(selectedProduct)
                .quantity(5)
                .unitPrice(offer.getNegotiatedPrice())
                .totalAmount(offer.getNegotiatedPrice().multiply(BigDecimal.valueOf(5)))
                .status(PurchaseOrderStatus.CONFIRMED)
                .confirmedAt(Instant.now())
                .build();

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);
        assertThat(savedPo.getId()).isNotNull();
        assertThat(savedPo.getTotalAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(purchaseOrderRepository.existsByProcurementId(saved.getId())).isTrue();
    }
}
