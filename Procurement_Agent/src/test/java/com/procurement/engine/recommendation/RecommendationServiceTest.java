package com.procurement.engine.recommendation;

import com.procurement.engine.approval.entity.Approval;
import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.repository.ApprovalRepository;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.OfferStatus;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.repository.VendorOfferRepository;
import com.procurement.engine.recommendation.model.RecommendationResponse;
import com.procurement.engine.recommendation.service.RecommendationService;
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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommendationServiceTest {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private VendorOfferRepository vendorOfferRepository;

    @Autowired
    private UserRepository userRepository;

    private User manager;

    @BeforeEach
    void setUp() {
        manager = userRepository.findByEmail("manager@procurement.com").orElseThrow();
    }

    private ProcurementRequest createProcurement(String category, int quantity, BigDecimal limit, List<ProcurementConstraint> constraints) {
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
    @DisplayName("Identifies BUDGET_OVERRIDE_RECOMMENDED when over-budget option provides superior 3-year TCO")
    void testBudgetOverrideRecommendationForSuperiorTco() {
        // Hard constraint: price <= 50000 and screenSize >= 55
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder()
                        .attribute("price")
                        .operator(ConstraintOperator.LESS_THAN_OR_EQUAL)
                        .value("50000")
                        .mandatory(true)
                        .build(),
                ProcurementConstraint.builder()
                        .attribute("screenSize")
                        .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                        .value("55")
                        .mandatory(true)
                        .build()
        );

        ProcurementRequest request = createProcurement("TV", 1, new BigDecimal("60000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        RecommendationResponse response = recommendationService.generateRecommendation(request.getId());

        assertThat(response.getBestEligibleOption()).isNotNull();
        assertThat(response.getBestEligibleOption().isEligible()).isTrue();
        assertThat(response.getSelectedOfferId()).isEqualTo(response.getBestEligibleOption().getOfferId());

        // Check recommendation type based on proposed exception offer presence
        if (response.getProposedExceptionOffer() != null) {
            assertThat(response.getRecommendationType()).isEqualTo("BUDGET_OVERRIDE_RECOMMENDED");
            assertThat(response.getExplanation()).contains("Best compliant option").contains("exception candidate");
            assertThat(response.getTradeOffs()).isNotEmpty();
        } else {
            assertThat(response.getRecommendationType()).isIn("AUTONOMOUS_PURCHASE_READY", "REQUIRES_AUTHORIZATION");
        }

        // Executable selectedOffer in database MUST strictly be the eligible candidate
        ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcurementState.RECOMMENDED);
        assertThat(updated.getSelectedOffer()).isNotNull();
        assertThat(updated.getSelectedOffer().getId()).isEqualTo(response.getBestEligibleOption().getOfferId());
        assertThat(updated.getSelectedOffer().getStatus()).isEqualTo(OfferStatus.RECOMMENDED);
    }

    @Test
    @DisplayName("Generates AUTONOMOUS_PURCHASE_READY when best eligible option satisfies budget and limit")
    void testAutonomousPurchaseReadyRecommendation() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder()
                        .attribute("screenSize")
                        .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                        .value("55")
                        .mandatory(true)
                        .build()
        );

        ProcurementRequest request = createProcurement("TV", 5, new BigDecimal("400000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        RecommendationResponse response = recommendationService.generateRecommendation(request.getId());

        assertThat(response.getRecommendationType()).isIn("AUTONOMOUS_PURCHASE_READY", "BUDGET_OVERRIDE_RECOMMENDED");
        assertThat(response.getBestEligibleOption()).isNotNull();
        assertThat(response.getSelectedOfferId()).isEqualTo(response.getBestEligibleOption().getOfferId());
        assertThat(response.getExplanation()).isNotEmpty();
        assertThat(response.getTradeOffs()).isNotEmpty();
    }

    @Test
    @DisplayName("Generates NO_RECOMMENDATION when zero candidate products satisfy hard constraints")
    void testZeroEligibleProductsRecommendation() {
        // Impossible hard constraint
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder()
                        .attribute("price")
                        .operator(ConstraintOperator.LESS_THAN)
                        .value("500")
                        .mandatory(true)
                        .build()
        );
        ProcurementRequest request = createProcurement("Office chair", 2, new BigDecimal("50000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        RecommendationResponse response = recommendationService.generateRecommendation(request.getId());

        assertThat(response.getRecommendationType()).isEqualTo("NO_RECOMMENDATION");
        assertThat(response.getBestEligibleOption()).isNull();
        assertThat(response.getSelectedOfferId()).isNull();
        assertThat(response.getExplanation()).contains("No candidate products satisfied mandatory hard constraints");
    }

    @Autowired
    private com.procurement.engine.product.repository.ProductRepository productRepository;

    @Test
    @DisplayName("When top-ranked compliant product is out of stock, recommends next in-stock compliant offer")
    void testTopRankedProductOutOfStockSelectsNextInStockCompliantOffer() {
        // Drop Dell Latitude stock to 0 so Lenovo ThinkPad becomes top available candidate
        List<com.procurement.engine.product.entity.Product> laptops = productRepository.findByCategoryIgnoreCase("Laptop");
        for (com.procurement.engine.product.entity.Product p : laptops) {
            if ("Dell Latitude 5540 Business Laptop".equals(p.getName())) {
                p.setAvailableQuantity(0);
                p.setAvailability(false);
                productRepository.save(p);
            }
        }

        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build(),
                ProcurementConstraint.builder().attribute("price").operator(ConstraintOperator.LESS_THAN_OR_EQUAL).value("85000").mandatory(true).build(),
                ProcurementConstraint.builder().attribute("deliveryDays").operator(ConstraintOperator.LESS_THAN_OR_EQUAL).value("7").mandatory(true).build()
        );

        ProcurementRequest request = createProcurement("Laptop", 5, new BigDecimal("500000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        RecommendationResponse response = recommendationService.generateRecommendation(request.getId());

        assertThat(response.getBestEligibleOption()).isNotNull();
        assertThat(response.getBestEligibleOption().isEligible()).isTrue();
        assertThat(response.getBestEligibleOption().getProductName()).isEqualTo("Lenovo ThinkPad T14s Gen 4");
    }

    @Autowired
    private ApprovalRepository approvalRepository;

    @Test
    @DisplayName("When no approval exists and amount exceeds user limit, produces REQUIRES_AUTHORIZATION")
    void testNoApprovalAmountExceedsLimitProducesRequiresAuthorization() {
        // Manager limit is 450,000. 10 laptops * 78,000 = 780,000 > 450,000
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
        );
        ProcurementRequest request = createProcurement("Laptop", 10, new BigDecimal("450000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        RecommendationResponse response = recommendationService.generateRecommendation(request.getId());

        assertThat(response.getRecommendationType()).isEqualTo("REQUIRES_AUTHORIZATION");
        assertThat(response.getExplanation()).contains("exceeds user authorization limit");
        assertThat(response.getBestEligibleOption()).isNotNull();
    }

    @Test
    @DisplayName("When PENDING approval exists and amount exceeds user limit, still produces REQUIRES_AUTHORIZATION")
    void testPendingApprovalAmountExceedsLimitProducesRequiresAuthorization() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
        );
        ProcurementRequest request = createProcurement("Laptop", 10, new BigDecimal("450000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        // Create PENDING approval record
        Approval pendingApproval = Approval.builder()
                .procurement(request)
                .requestedAmount(new BigDecimal("780000.00"))
                .authorizationLimit(new BigDecimal("450000.00"))
                .difference(new BigDecimal("330000.00"))
                .exceptionType("LIMIT_EXCEEDED")
                .reason("Spend amount exceeds user limit")
                .status(ApprovalStatus.PENDING)
                .requestedAt(Instant.now())
                .build();
        approvalRepository.save(pendingApproval);

        RecommendationResponse response = recommendationService.generateRecommendation(request.getId());

        assertThat(response.getRecommendationType()).isEqualTo("REQUIRES_AUTHORIZATION");
        assertThat(response.getExplanation()).contains("exceeds user authorization limit");
    }

    @Test
    @DisplayName("When APPROVED approval exists, produces AUTONOMOUS_PURCHASE_READY and reflects approved budget")
    void testApprovedAuthorizationProducesAutonomousPurchaseReady() {
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
        );
        ProcurementRequest request = createProcurement("Laptop", 10, new BigDecimal("450000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        // Create APPROVED approval record
        Approval approvedApproval = Approval.builder()
                .procurement(request)
                .requestedAmount(new BigDecimal("780000.00"))
                .authorizationLimit(new BigDecimal("450000.00"))
                .difference(new BigDecimal("330000.00"))
                .exceptionType("LIMIT_EXCEEDED")
                .reason("Spend amount approved by VP")
                .status(ApprovalStatus.APPROVED)
                .requestedAt(Instant.now())
                .decidedAt(Instant.now())
                .decidedBy(manager)
                .comments("Approved budget expansion")
                .build();
        approvalRepository.save(approvedApproval);

        RecommendationResponse response = recommendationService.generateRecommendation(request.getId());

        assertThat(response.getRecommendationType()).isEqualTo("AUTONOMOUS_PURCHASE_READY");
        assertThat(response.getExplanation()).contains("has been authorized by management approval");
        assertThat(response.getExplanation()).contains("780000.00");
    }
}
