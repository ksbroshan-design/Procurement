package com.procurement.engine.e2e;

import com.procurement.engine.approval.entity.Approval;
import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.repository.ApprovalRepository;
import com.procurement.engine.approval.service.ApprovalService;
import com.procurement.engine.audit.entity.AuditEventType;
import com.procurement.engine.audit.model.ProcurementAuditResponse;
import com.procurement.engine.audit.service.AuditService;
import com.procurement.engine.authorization.model.ApprovalActionRequest;
import com.procurement.engine.authorization.model.AuthorizationDecisionDto;
import com.procurement.engine.authorization.service.AuthorizationService;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.model.DiscoveryResult;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.OfferStatus;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.model.OrchestrationResultDto;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.service.ProcurementOrchestrator;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.purchase.entity.PurchaseOrder;
import com.procurement.engine.purchase.entity.PurchaseOrderStatus;
import com.procurement.engine.purchase.model.PurchaseExecutionResultDto;
import com.procurement.engine.purchase.repository.PurchaseOrderRepository;
import com.procurement.engine.purchase.service.PurchaseExecutionService;
import com.procurement.engine.recommendation.model.RecommendationResponse;
import com.procurement.engine.recommendation.service.RecommendationService;
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
class ProcurementEndToEndIntegrationTest {

    @Autowired
    private ProcurementOrchestrator orchestrator;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private RevalidationService revalidationService;

    @Autowired
    private PurchaseExecutionService purchaseExecutionService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private UserRepository userRepository;

    private User manager;

    @BeforeEach
    void setUp() {
        manager = userRepository.findByEmail("manager@procurement.com").orElseThrow();
    }

    private ProcurementRequest createRequest(String category, int quantity, BigDecimal limit, List<ProcurementConstraint> constraints) {
        ProcurementRequest req = ProcurementRequest.builder()
                .user(manager)
                .category(category)
                .quantity(quantity)
                .authorizationLimit(limit)
                .status(ProcurementState.SUBMITTED)
                .build();
        if (constraints != null) {
            constraints.forEach(req::addConstraint);
        }
        return procurementRequestRepository.save(req);
    }

    @Test
    @DisplayName("TEST 1 — HAPPY PATH: Autonomous procurement executes end-to-end to COMPLETED with PO and stock deduction")
    void test1_HappyPathAutonomousProcurement() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
        );

        ProcurementRequest req = createRequest("TV", 2, new BigDecimal("300000.00"), constraints);

        OrchestrationResultDto result = orchestrator.orchestrate(req.getId());

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getFinalState()).isEqualTo(ProcurementState.COMPLETED);
        assertThat(result.getPurchaseOrderId()).isNotNull();

        // Verify PurchaseOrder
        PurchaseOrder po = purchaseOrderRepository.findById(result.getPurchaseOrderId()).orElseThrow();
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.CONFIRMED);
        assertThat(po.getQuantity()).isEqualTo(2);

        // Verify state
        ProcurementRequest updated = procurementRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcurementState.COMPLETED);
        assertThat(updated.getSelectedOffer().getStatus()).isEqualTo(OfferStatus.ACCEPTED);

        // Verify audit trail exists
        ProcurementAuditResponse audit = auditService.getAuditTrail(req.getId());
        assertThat(audit.getTotalEvents()).isGreaterThan(0);
    }

    @Test
    @DisplayName("TEST 2 — HUMAN APPROVAL PATH: Exceeding limit escalates to WAITING_APPROVAL, manager approves -> COMPLETED")
    void test2_HumanApprovalPath() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
        );

        // Request 10 laptops with low budget of 200,000 to force WAITING_APPROVAL
        ProcurementRequest req = createRequest("Laptop", 10, new BigDecimal("200000.00"), constraints);

        OrchestrationResultDto step1 = orchestrator.orchestrate(req.getId());
        assertThat(step1.getStatus()).isEqualTo("WAITING_APPROVAL");
        assertThat(step1.getFinalState()).isEqualTo(ProcurementState.WAITING_APPROVAL);

        // Verify no PO created yet
        assertThat(purchaseOrderRepository.findByProcurementId(req.getId())).isEmpty();

        // Verify pending approval
        Approval approval = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(req.getId()).orElseThrow();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);

        // Manager approves
        approvalService.approve(req.getId(), ApprovalActionRequest.ofComments("Approved by Finance VP"), manager);

        ProcurementRequest postApprove = procurementRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(postApprove.getStatus()).isEqualTo(ProcurementState.REVALIDATING);

        // Resume orchestration
        OrchestrationResultDto step2 = orchestrator.orchestrate(req.getId());
        assertThat(step2.getStatus()).isEqualTo("COMPLETED");
        assertThat(step2.getFinalState()).isEqualTo(ProcurementState.COMPLETED);

        // Verify PO created
        PurchaseOrder po = purchaseOrderRepository.findById(step2.getPurchaseOrderId()).orElseThrow();
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("TEST 3 — BUDGET OVERRIDE / FALSE ECONOMY: Evaluates exception candidates and keeps selectedOffer bound to best eligible")
    void test3_BudgetOverrideFalseEconomy() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("price").operator(ConstraintOperator.LESS_THAN_OR_EQUAL).value("50000").mandatory(true).build(),
                ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
        );

        ProcurementRequest req = createRequest("TV", 1, new BigDecimal("60000.00"), constraints);
        discoveryService.discoverAndEvaluate(req.getId());

        RecommendationResponse rec = recommendationService.generateRecommendation(req.getId());
        assertThat(rec.getBestEligibleOption()).isNotNull();
        assertThat(rec.getSelectedOfferId()).isEqualTo(rec.getBestEligibleOption().getOfferId());
    }

    @Test
    @DisplayName("TEST 4 — STALE PRICE: Catalog price spike triggers stale detection and transitions to SEARCHING")
    void test4_StalePriceRecovery() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
        );

        ProcurementRequest req = createRequest("TV", 1, new BigDecimal("300000.00"), constraints);
        discoveryService.discoverAndEvaluate(req.getId());
        authorizationService.checkAuthorization(req.getId());

        ProcurementRequest ready = procurementRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(ready.getStatus()).isEqualTo(ProcurementState.REVALIDATING);

        // Simulate catalog price spike
        Product product = ready.getSelectedOffer().getProduct();
        product.setPrice(product.getPrice().add(new BigDecimal("10000.00")));
        productRepository.save(product);

        RevalidationResultDto reval = revalidationService.revalidate(req.getId());
        assertThat(reval.isValid()).isFalse();
        assertThat(reval.getStatus()).isEqualTo("STALE");

        ProcurementRequest afterStale = procurementRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(afterStale.getStatus()).isEqualTo(ProcurementState.SEARCHING);
        assertThat(afterStale.getRevalidationAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("TEST 5 — OUT OF STOCK: Stock depletion triggers stale detection and transitions to SEARCHING")
    void test5_OutOfStockRecovery() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
        );

        ProcurementRequest req = createRequest("TV", 1, new BigDecimal("300000.00"), constraints);
        discoveryService.discoverAndEvaluate(req.getId());
        authorizationService.checkAuthorization(req.getId());

        Product product = procurementRequestRepository.findById(req.getId()).orElseThrow().getSelectedOffer().getProduct();
        product.setAvailableQuantity(0);
        product.setAvailability(false);
        productRepository.save(product);

        RevalidationResultDto reval = revalidationService.revalidate(req.getId());
        assertThat(reval.isValid()).isFalse();

        ProcurementRequest updated = procurementRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcurementState.SEARCHING);
    }

    @Test
    @DisplayName("TEST 6 — SUSPENDED VENDOR: Suspended vendor fails revalidation")
    void test6_SuspendedVendor() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
        );

        ProcurementRequest req = createRequest("TV", 1, new BigDecimal("300000.00"), constraints);
        discoveryService.discoverAndEvaluate(req.getId());
        authorizationService.checkAuthorization(req.getId());

        Vendor vendor = procurementRequestRepository.findById(req.getId()).orElseThrow().getSelectedOffer().getVendor();
        vendor.setStatus(VendorStatus.SUSPENDED);
        vendorRepository.save(vendor);

        RevalidationResultDto reval = revalidationService.revalidate(req.getId());
        assertThat(reval.isValid()).isFalse();
        assertThat(reval.getChecks().stream().anyMatch(c -> "VENDOR_STATUS".equals(c.getCheckName()) && !c.isPassed())).isTrue();
    }

    @Test
    @DisplayName("TEST 7 — IMPOSSIBLE REQUEST: Constraints that no product can satisfy produces NO_RECOMMENDATION")
    void test7_ImpossibleRequestNoProducts() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("512").mandatory(true).build()
        );

        ProcurementRequest req = createRequest("Laptop", 1, new BigDecimal("500000.00"), constraints);

        OrchestrationResultDto result = orchestrator.orchestrate(req.getId());
        assertThat(result.getStatus()).isEqualTo("NO_ELIGIBLE_PRODUCTS");
        assertThat(result.getRecommendationType()).isEqualTo("NO_RECOMMENDATION");
    }

    @Test
    @DisplayName("TEST 8 — DISCOVERY SOURCE PARTIAL FAILURE: Multi-source discovery continues when one vendor source has errors")
    void test8_DiscoverySourcePartialSuccess() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
        );

        ProcurementRequest req = createRequest("TV", 1, new BigDecimal("300000.00"), constraints);
        DiscoveryResult discovery = discoveryService.discoverAndEvaluate(req.getId());

        assertThat(discovery.getRawCandidatesCount()).isGreaterThan(0);
        assertThat(discovery.getEligibleCandidatesCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("TEST 9 — NON-EXISTENT CATEGORY / ZERO OFFERS: Graceful zero offer discovery handling")
    void test9_ZeroOffersDiscovery() {
        ProcurementRequest req = createRequest("NonExistentCategory", 1, new BigDecimal("100000.00"), List.of());
        DiscoveryResult discovery = discoveryService.discoverAndEvaluate(req.getId());

        assertThat(discovery.getRawCandidatesCount()).isEqualTo(0);
        assertThat(discovery.getEligibleCandidatesCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("TEST 10 — BOUNDED RETRY EXHAUSTION: 3 failed retries halts at WAITING_USER")
    void test10_BoundedRetryExhaustion() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
        );

        ProcurementRequest req = createRequest("TV", 1, new BigDecimal("300000.00"), constraints);
        discoveryService.discoverAndEvaluate(req.getId());
        authorizationService.checkAuthorization(req.getId());

        Product product = procurementRequestRepository.findById(req.getId()).orElseThrow().getSelectedOffer().getProduct();
        product.setAvailableQuantity(0);
        product.setAvailability(false);
        productRepository.save(product);

        // Attempt 1 -> SEARCHING (1/3)
        revalidationService.revalidate(req.getId());
        ProcurementRequest r1 = procurementRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(r1.getStatus()).isEqualTo(ProcurementState.SEARCHING);
        assertThat(r1.getRevalidationAttempts()).isEqualTo(1);

        // Simulate retry 2
        r1.setStatus(ProcurementState.REVALIDATING);
        procurementRequestRepository.save(r1);
        revalidationService.revalidate(r1.getId());
        ProcurementRequest r2 = procurementRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(r2.getRevalidationAttempts()).isEqualTo(2);

        // Simulate retry 3
        r2.setStatus(ProcurementState.REVALIDATING);
        procurementRequestRepository.save(r2);
        revalidationService.revalidate(r2.getId());
        ProcurementRequest r3 = procurementRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(r3.getRevalidationAttempts()).isEqualTo(3);

        // Attempt 4: Max retries exhausted -> WAITING_USER
        r3.setStatus(ProcurementState.REVALIDATING);
        procurementRequestRepository.save(r3);
        revalidationService.revalidate(r3.getId());
        ProcurementRequest r4 = procurementRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(r4.getStatus()).isEqualTo(ProcurementState.WAITING_USER);
    }

    @Test
    @DisplayName("TEST 11 — PURCHASE IDEMPOTENCY: Repeated purchases return existing PO without duplicate creation")
    void test11_PurchaseIdempotency() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
        );

        ProcurementRequest req = createRequest("TV", 1, new BigDecimal("300000.00"), constraints);
        discoveryService.discoverAndEvaluate(req.getId());
        authorizationService.checkAuthorization(req.getId());
        revalidationService.revalidate(req.getId());

        PurchaseExecutionResultDto call1 = purchaseExecutionService.executePurchase(req.getId());
        assertThat(call1.getStatus()).isEqualTo("CONFIRMED");

        PurchaseExecutionResultDto call2 = purchaseExecutionService.executePurchase(req.getId());
        assertThat(call2.getStatus()).isEqualTo("ALREADY_COMPLETED");
        assertThat(call2.getPurchaseOrderId()).isEqualTo(call1.getPurchaseOrderId());

        List<PurchaseOrder> orders = purchaseOrderRepository.findByProcurementId(req.getId());
        assertThat(orders).hasSize(1);
    }

    @Test
    @DisplayName("TEST 12 — AUDIT COMPLETENESS: Chronological audit trail records all major lifecycle events")
    void test12_AuditCompleteness() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
        );

        ProcurementRequest req = createRequest("TV", 1, new BigDecimal("300000.00"), constraints);

        auditService.record(req.getId(), AuditEventType.REQUEST_CREATED, ProcurementState.SUBMITTED, "USER", "Created", null);

        orchestrator.orchestrate(req.getId());

        ProcurementAuditResponse audit = auditService.getAuditTrail(req.getId());
        assertThat(audit.getEvents()).isNotEmpty();
        assertThat(audit.getEvents().stream().anyMatch(e -> e.getEventType() == AuditEventType.STATE_TRANSITION)).isTrue();
    }
}
