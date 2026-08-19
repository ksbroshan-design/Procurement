package com.procurement.engine.revalidation;

import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.repository.ApprovalRepository;
import com.procurement.engine.approval.service.ApprovalService;
import com.procurement.engine.authorization.model.ApprovalActionRequest;
import com.procurement.engine.authorization.service.AuthorizationService;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.revalidation.model.RevalidationResultDto;
import com.procurement.engine.revalidation.service.RevalidationService;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import com.procurement.engine.vendor.entity.Vendor;
import com.procurement.engine.vendor.entity.VendorStatus;
import com.procurement.engine.vendor.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RevalidationServiceTest {

    @Autowired
    private RevalidationService revalidationService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

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
                .quantity(1)
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

    @Nested
    @DisplayName("Successful Revalidation Tests")
    class SuccessfulRevalidationTests {

        @Test
        @DisplayName("Revalidation passes for valid offer and advances state to PURCHASING")
        void testSuccessfulRevalidation() {
            ProcurementRequest request = createReadyProcurement();
            assertThat(request.getStatus()).isEqualTo(ProcurementState.REVALIDATING);

            RevalidationResultDto result = revalidationService.revalidate(request.getId());

            assertThat(result.isValid()).isTrue();
            assertThat(result.getStatus()).isEqualTo("VALID");
            assertThat(result.getNextState()).isEqualTo("PURCHASING");
            assertThat(result.getChecks()).isNotEmpty();
            assertThat(result.getChecks()).allMatch(check -> check.isPassed());

            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.PURCHASING);
        }
    }

    @Nested
    @DisplayName("Stale Offer Detection Tests")
    class StaleOfferTests {

        @Test
        @DisplayName("Price change marks offer STALE even if below budget and transitions to SEARCHING for retry")
        void testPriceChangeMarksStale() {
            ProcurementRequest request = createReadyProcurement();
            VendorOffer selected = request.getSelectedOffer();
            Product product = selected.getProduct();

            // Simulate price spike in vendor catalog
            BigDecimal originalPrice = product.getPrice();
            product.setPrice(originalPrice.add(new BigDecimal("5000.00")));
            productRepository.save(product);

            RevalidationResultDto result = revalidationService.revalidate(request.getId());

            assertThat(result.isValid()).isFalse();
            assertThat(result.getStatus()).isEqualTo("STALE");
            assertThat(result.getChecks().stream().anyMatch(c -> "PRICE_STABILITY".equals(c.getCheckName()) && !c.isPassed())).isTrue();

            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.SEARCHING);
            assertThat(updated.getRevalidationAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("Insufficient inventory marks offer STALE and transitions to SEARCHING")
        void testInsufficientInventoryMarksStale() {
            ProcurementRequest request = createReadyProcurement();
            VendorOffer selected = request.getSelectedOffer();
            Product product = selected.getProduct();

            // Drop stock to 0
            product.setAvailableQuantity(0);
            product.setAvailability(false);
            productRepository.save(product);

            RevalidationResultDto result = revalidationService.revalidate(request.getId());

            assertThat(result.isValid()).isFalse();
            assertThat(result.getStatus()).isEqualTo("STALE");
            assertThat(result.getChecks().stream().anyMatch(c -> "INVENTORY".equals(c.getCheckName()) && !c.isPassed())).isTrue();

            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.SEARCHING);
        }

        @Test
        @DisplayName("Suspended/inactive vendor marks offer STALE")
        void testInactiveVendorMarksStale() {
            ProcurementRequest request = createReadyProcurement();
            VendorOffer selected = request.getSelectedOffer();
            Vendor vendor = selected.getVendor();

            // Suspend vendor
            vendor.setStatus(VendorStatus.SUSPENDED);
            vendorRepository.save(vendor);

            RevalidationResultDto result = revalidationService.revalidate(request.getId());

            assertThat(result.isValid()).isFalse();
            assertThat(result.getStatus()).isEqualTo("STALE");
            assertThat(result.getChecks().stream().anyMatch(c -> "VENDOR_STATUS".equals(c.getCheckName()) && !c.isPassed())).isTrue();

            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.SEARCHING);
        }

        @Test
        @DisplayName("Bounded retry recovery: 3 failed retries transitions state to WAITING_USER")
        void testBoundedRetryExhaustion() {
            ProcurementRequest request = createReadyProcurement();
            VendorOffer selected = request.getSelectedOffer();
            Product product = selected.getProduct();

            // Make product persistently out of stock
            product.setAvailableQuantity(0);
            product.setAvailability(false);
            productRepository.save(product);

            // Attempt 1: REVALIDATING -> SEARCHING (attempt = 1)
            revalidationService.revalidate(request.getId());
            ProcurementRequest r1 = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(r1.getStatus()).isEqualTo(ProcurementState.SEARCHING);
            assertThat(r1.getRevalidationAttempts()).isEqualTo(1);

            // Reset state to REVALIDATING to simulate retry discovery
            r1.setStatus(ProcurementState.REVALIDATING);
            procurementRequestRepository.save(r1);

            // Attempt 2: REVALIDATING -> SEARCHING (attempt = 2)
            revalidationService.revalidate(r1.getId());
            ProcurementRequest r2 = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(r2.getStatus()).isEqualTo(ProcurementState.SEARCHING);
            assertThat(r2.getRevalidationAttempts()).isEqualTo(2);

            // Reset state to REVALIDATING for attempt 3
            r2.setStatus(ProcurementState.REVALIDATING);
            procurementRequestRepository.save(r2);

            // Attempt 3: REVALIDATING -> SEARCHING (attempt = 3)
            revalidationService.revalidate(r2.getId());
            ProcurementRequest r3 = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(r3.getStatus()).isEqualTo(ProcurementState.SEARCHING);
            assertThat(r3.getRevalidationAttempts()).isEqualTo(3);

            // Reset state to REVALIDATING for attempt 4 (exhaustion)
            r3.setStatus(ProcurementState.REVALIDATING);
            procurementRequestRepository.save(r3);

            // Attempt 4: Exhausted -> WAITING_USER
            revalidationService.revalidate(r3.getId());
            ProcurementRequest r4 = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(r4.getStatus()).isEqualTo(ProcurementState.WAITING_USER);
        }
    }
}
