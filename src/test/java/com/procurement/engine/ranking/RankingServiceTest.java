package com.procurement.engine.ranking;

import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.ranking.model.ProcurementRankingResponse;
import com.procurement.engine.ranking.model.RankedOfferDto;
import com.procurement.engine.ranking.service.RankingService;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RankingServiceTest {

    @Autowired
    private RankingService rankingService;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

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
    @DisplayName("Multi-dimensional ranking prioritizes lower TCO and quality over raw sticker price")
    void testRankingPrioritizesTcoAndQuality() {
        // TV category: Screen size >= 55
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder()
                        .attribute("screenSize")
                        .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                        .value("55")
                        .mandatory(true)
                        .build()
        );

        ProcurementRequest request = createProcurement("TV", 5, new BigDecimal("350000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        ProcurementRankingResponse response = rankingService.rankOffers(request.getId());

        assertThat(response.getEligibleOffers()).isNotEmpty();
        assertThat(response.getWeightsUsed()).containsKeys("tco", "price", "reliability", "warranty");

        RankedOfferDto topOffer = response.getTopRankedOffer();
        assertThat(topOffer).isNotNull();
        assertThat(topOffer.getRank()).isEqualTo(1);
        assertThat(topOffer.isEligible()).isTrue();
        assertThat(topOffer.getTotalScore()).isGreaterThan(BigDecimal.ZERO);

        // Verification of descending ranking order
        List<RankedOfferDto> eligible = response.getEligibleOffers();
        for (int i = 0; i < eligible.size() - 1; i++) {
            assertThat(eligible.get(i).getTotalScore())
                    .isGreaterThanOrEqualTo(eligible.get(i + 1).getTotalScore());
            assertThat(eligible.get(i).getRank()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("Ineligible offers are strictly quarantined to Pool B and never enter Pool A rankings")
    void testStrictPoolIsolationForIneligibleOffers() {
        // Strict price hard constraint: price <= 50,000
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

        ProcurementRankingResponse response = rankingService.rankOffers(request.getId());

        // Pool A should contain only eligible products (price <= 50,000)
        assertThat(response.getEligibleOffers()).allMatch(o -> o.isEligible() && o.getUnitPrice().compareTo(new BigDecimal("50000.00")) <= 0);

        // Top eligible is rank 1 in Pool A, distinct from top exception
        if (response.getTopExceptionOffer() != null) {
            assertThat(response.getTopExceptionOffer().isExceptionOffer()).isTrue();
        }
    }

    @Autowired
    private com.procurement.engine.product.repository.ProductRepository productRepository;

    @Test
    @DisplayName("Out-of-stock offers (availability=false) are quarantined to Pool B even if constraints pass")
    void testRankingQuarantinesOutOfStockOffersToPoolB() {
        List<com.procurement.engine.product.entity.Product> laptops = productRepository.findByCategoryIgnoreCase("Laptop");
        for (com.procurement.engine.product.entity.Product p : laptops) {
            if ("Dell Latitude 5540 Business Laptop".equals(p.getName())) {
                p.setAvailability(false);
                p.setAvailableQuantity(0);
                productRepository.save(p);
            }
        }

        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
        );

        ProcurementRequest request = createProcurement("Laptop", 5, new BigDecimal("500000.00"), constraints);
        discoveryService.discoverAndEvaluate(request.getId());

        ProcurementRankingResponse response = rankingService.rankOffers(request.getId());

        assertThat(response.getEligibleOffers()).noneMatch(o -> "Dell Latitude 5540 Business Laptop".equals(o.getProductName()));
        assertThat(response.getExceptionOffers()).anyMatch(o -> "Dell Latitude 5540 Business Laptop".equals(o.getProductName()));
    }

    @Autowired
    private com.procurement.engine.approval.repository.ApprovalRepository approvalRepository;

    @Autowired
    private com.procurement.engine.approval.service.ApprovalService approvalService;

    @Autowired
    private com.procurement.engine.recommendation.service.RecommendationService recommendationService;

    @Autowired
    private com.procurement.engine.authorization.service.AuthorizationService authorizationService;

    @Autowired
    private com.procurement.engine.procurement.service.ProcurementOrchestrator procurementOrchestrator;

    @Autowired
    private com.procurement.engine.purchase.repository.PurchaseOrderRepository purchaseOrderRepository;

    @org.junit.jupiter.api.Nested
    @DisplayName("Demo 4 HITL Authorization Limit & Approved Exception Regression Tests")
    class Demo4HitlAuthorizationRegressionTests {

        @Test
        @DisplayName("1. User limit ₹50,000, offer ₹56,000, no approval -> offer remains outside Pool A and requires approval")
        void test1_UserLimit50k_Offer56k_NoApproval_RemainsOutsidePoolA_RequiresApproval() {
            User employee = userRepository.findByEmail("user@procurement.com").orElseThrow();
            assertThat(employee.getAuthorizationLimit()).isEqualByComparingTo("50000.00");

            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build(),
                    ProcurementConstraint.builder().attribute("panelType").operator(ConstraintOperator.EQUALS).value("OLED").mandatory(true).build()
            );

            ProcurementRequest request = ProcurementRequest.builder()
                    .user(employee)
                    .category("TV")
                    .quantity(1)
                    .authorizationLimit(new BigDecimal("50000.00"))
                    .status(ProcurementState.SUBMITTED)
                    .build();
            constraints.forEach(request::addConstraint);
            ProcurementRequest saved = procurementRequestRepository.save(request);

            discoveryService.discoverAndEvaluate(saved.getId());

            // 1. Ranking Service check: Pool A is empty, Pool B contains over-budget LG C3 (56,000)
            ProcurementRankingResponse ranking = rankingService.rankOffers(saved.getId());
            assertThat(ranking.getEligibleOffers()).isEmpty();
            assertThat(ranking.getExceptionOffers()).isNotEmpty();
            assertThat(ranking.getTopExceptionOffer()).isNotNull();
            assertThat(ranking.getTopExceptionOffer().isBudgetExceeded()).isTrue();
            assertThat(ranking.getTopExceptionOffer().getPrice()).isEqualByComparingTo("56000.00");

            // 2. Recommendation Service check: produces REQUIRES_AUTHORIZATION
            com.procurement.engine.recommendation.model.RecommendationResponse recommendation =
                    recommendationService.generateRecommendation(saved.getId());
            assertThat(recommendation.getRecommendationType()).isEqualTo("REQUIRES_AUTHORIZATION");

            // 3. Authorization Service check: produces REQUIRES_APPROVAL and transitions to WAITING_APPROVAL
            com.procurement.engine.authorization.model.AuthorizationDecisionDto authDecision =
                    authorizationService.checkAuthorization(saved.getId());
            assertThat(authDecision.isWithinAuthorization()).isFalse();
            assertThat(authDecision.getDecision()).isEqualTo("REQUIRES_APPROVAL");
            assertThat(authDecision.getNextState()).isEqualTo("WAITING_APPROVAL");
            assertThat(authDecision.getAuthorizationLimit()).isEqualByComparingTo("50000.00");
            assertThat(authDecision.getTotalRequestedAmount()).isEqualByComparingTo("56000.00");
            assertThat(authDecision.getExcessAmount()).isEqualByComparingTo("6000.00");

            ProcurementRequest afterCheck = procurementRequestRepository.findById(saved.getId()).orElseThrow();
            assertThat(afterCheck.getStatus()).isEqualTo(ProcurementState.WAITING_APPROVAL);
        }

        @Test
        @DisplayName("2. Same procurement after APPROVED approval for ₹56,000 -> offer is admitted to Pool A")
        void test2_SameProcurement_AfterApprovedApprovalFor56k_AdmittedToPoolA() {
            User employee = userRepository.findByEmail("user@procurement.com").orElseThrow();
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build(),
                    ProcurementConstraint.builder().attribute("panelType").operator(ConstraintOperator.EQUALS).value("OLED").mandatory(true).build()
            );

            ProcurementRequest request = ProcurementRequest.builder()
                    .user(employee)
                    .category("TV")
                    .quantity(1)
                    .authorizationLimit(new BigDecimal("50000.00"))
                    .status(ProcurementState.SUBMITTED)
                    .build();
            constraints.forEach(request::addConstraint);
            ProcurementRequest saved = procurementRequestRepository.save(request);

            discoveryService.discoverAndEvaluate(saved.getId());
            authorizationService.checkAuthorization(saved.getId());

            // Save APPROVED approval record for ₹56,000.00
            com.procurement.engine.approval.entity.Approval approval = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(saved.getId()).orElseThrow();
            approval.setStatus(com.procurement.engine.approval.entity.ApprovalStatus.APPROVED);
            approval.setRequestedAmount(new BigDecimal("56000.00"));
            approvalRepository.save(approval);

            // Re-run Ranking Service: LG C3 is now admitted into Pool A
            ProcurementRankingResponse ranking = rankingService.rankOffers(saved.getId());
            assertThat(ranking.getEligibleOffers()).isNotEmpty();
            assertThat(ranking.getTopRankedOffer()).isNotNull();
            assertThat(ranking.getTopRankedOffer().getProductName()).isEqualTo("LG C3 55-Inch 4K OLED evo Smart TV");
            assertThat(ranking.getTopRankedOffer().isEligible()).isTrue();
            assertThat(ranking.getTopRankedOffer().getPrice()).isEqualByComparingTo("56000.00");
        }

        @Test
        @DisplayName("3. Multiple offers after approval: offers <= approved in Pool A, offers > approved remain in Pool B")
        void test3_MultipleOffersAfterApproval_OffersUnderLimitInPoolA_OffersOverLimitInPoolB() {
            User employee = userRepository.findByEmail("user@procurement.com").orElseThrow();
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build()
            );

            ProcurementRequest request = ProcurementRequest.builder()
                    .user(employee)
                    .category("TV")
                    .quantity(1)
                    .authorizationLimit(new BigDecimal("50000.00"))
                    .status(ProcurementState.SUBMITTED)
                    .build();
            constraints.forEach(request::addConstraint);
            ProcurementRequest saved = procurementRequestRepository.save(request);

            discoveryService.discoverAndEvaluate(saved.getId());

            // Create APPROVED approval record for ₹60,000.00
            com.procurement.engine.approval.entity.Approval approval = com.procurement.engine.approval.entity.Approval.builder()
                    .procurement(saved)
                    .requestedAmount(new BigDecimal("60000.00"))
                    .authorizationLimit(new BigDecimal("50000.00"))
                    .difference(new BigDecimal("10000.00"))
                    .exceptionType("LIMIT_EXCEEDED")
                    .reason("Approved up to ₹60,000 by Director")
                    .status(com.procurement.engine.approval.entity.ApprovalStatus.APPROVED)
                    .requestedAt(java.time.Instant.now())
                    .build();
            approvalRepository.save(approval);

            ProcurementRankingResponse ranking = rankingService.rankOffers(saved.getId());

            // Offers <= 60,000 (LG C3 56k, Samsung QLED 58k, VisionMax 42k) are in Pool A
            assertThat(ranking.getEligibleOffers()).allMatch(o -> o.getPrice().compareTo(new BigDecimal("60000.00")) <= 0);

            // Sony BRAVIA 68k (> 60k) remains in Pool B
            assertThat(ranking.getExceptionOffers()).anyMatch(o -> "Sony BRAVIA XR 55-Inch 4K OLED TV".equals(o.getProductName())
                    && o.getPrice().compareTo(new BigDecimal("60000.00")) > 0);
        }

        @Test
        @DisplayName("4. Approval must not modify User.authorizationLimit (User entity limit remains immutable)")
        void test4_ApprovalDoesNotModifyUserAuthorizationLimit() {
            User employee = userRepository.findByEmail("user@procurement.com").orElseThrow();
            BigDecimal initialLimit = employee.getAuthorizationLimit();
            assertThat(initialLimit).isEqualByComparingTo("50000.00");

            ProcurementRequest request = ProcurementRequest.builder()
                    .user(employee)
                    .category("TV")
                    .quantity(1)
                    .authorizationLimit(new BigDecimal("50000.00"))
                    .status(ProcurementState.SUBMITTED)
                    .build();
            ProcurementRequest saved = procurementRequestRepository.save(request);

            discoveryService.discoverAndEvaluate(saved.getId());

            com.procurement.engine.approval.entity.Approval approval = com.procurement.engine.approval.entity.Approval.builder()
                    .procurement(saved)
                    .requestedAmount(new BigDecimal("56000.00"))
                    .authorizationLimit(new BigDecimal("50000.00"))
                    .difference(new BigDecimal("6000.00"))
                    .exceptionType("LIMIT_EXCEEDED")
                    .reason("Approved")
                    .status(com.procurement.engine.approval.entity.ApprovalStatus.APPROVED)
                    .requestedAt(java.time.Instant.now())
                    .build();
            approvalRepository.save(approval);

            rankingService.rankOffers(saved.getId());

            // Verify User entity limit remains unchanged
            User reloadedUser = userRepository.findByEmail("user@procurement.com").orElseThrow();
            assertThat(reloadedUser.getAuthorizationLimit()).isEqualByComparingTo(initialLimit);
        }

        @Test
        @DisplayName("5. Client-supplied authorizationLimit must never override User.authorizationLimit")
        void test5_ClientSuppliedAuthorizationLimitNeverOverridesUserLimit() {
            User employee = userRepository.findByEmail("user@procurement.com").orElseThrow();

            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build(),
                    ProcurementConstraint.builder().attribute("panelType").operator(ConstraintOperator.EQUALS).value("OLED").mandatory(true).build()
            );

            // Client attempts to pass inflated authorizationLimit of ₹999,999.00 in request
            ProcurementRequest request = ProcurementRequest.builder()
                    .user(employee)
                    .category("TV")
                    .quantity(1)
                    .authorizationLimit(new BigDecimal("999999.00"))
                    .status(ProcurementState.SUBMITTED)
                    .build();
            constraints.forEach(request::addConstraint);
            ProcurementRequest saved = procurementRequestRepository.save(request);

            discoveryService.discoverAndEvaluate(saved.getId());

            // Must evaluate against user's actual limit ₹50,000 -> Pool A is empty
            ProcurementRankingResponse ranking = rankingService.rankOffers(saved.getId());
            assertThat(ranking.getEligibleOffers()).isEmpty();

            com.procurement.engine.authorization.model.AuthorizationDecisionDto decision =
                    authorizationService.checkAuthorization(saved.getId());
            assertThat(decision.isWithinAuthorization()).isFalse();
            assertThat(decision.getAuthorizationLimit()).isEqualByComparingTo("50000.00");
        }

        @Test
        @DisplayName("6. Approved offer must survive ranking and reach recommendation")
        void test6_ApprovedOfferSurvivesRankingAndReachesRecommendation() {
            User employee = userRepository.findByEmail("user@procurement.com").orElseThrow();
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build(),
                    ProcurementConstraint.builder().attribute("panelType").operator(ConstraintOperator.EQUALS).value("OLED").mandatory(true).build()
            );

            ProcurementRequest request = ProcurementRequest.builder()
                    .user(employee)
                    .category("TV")
                    .quantity(1)
                    .authorizationLimit(new BigDecimal("50000.00"))
                    .status(ProcurementState.SUBMITTED)
                    .build();
            constraints.forEach(request::addConstraint);
            ProcurementRequest saved = procurementRequestRepository.save(request);

            discoveryService.discoverAndEvaluate(saved.getId());
            authorizationService.checkAuthorization(saved.getId());

            // Approve the pending request
            com.procurement.engine.approval.entity.Approval approval = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(saved.getId()).orElseThrow();
            approval.setStatus(com.procurement.engine.approval.entity.ApprovalStatus.APPROVED);
            approval.setRequestedAmount(new BigDecimal("56000.00"));
            approvalRepository.save(approval);

            com.procurement.engine.recommendation.model.RecommendationResponse recommendation =
                    recommendationService.generateRecommendation(saved.getId());

            assertThat(recommendation.getRecommendationType()).isEqualTo("AUTONOMOUS_PURCHASE_READY");
            assertThat(recommendation.getBestEligibleOption()).isNotNull();
            assertThat(recommendation.getBestEligibleOption().getProductName()).isEqualTo("LG C3 55-Inch 4K OLED evo Smart TV");
            assertThat(recommendation.getExplanation()).contains("authorized by management approval");
        }

        @Test
        @DisplayName("7. Full Demo 4 flow: WAITING_APPROVAL -> APPROVED -> REVALIDATING -> PURCHASING -> COMPLETED with 1 PurchaseOrder")
        void test7_FullDemo4Flow_EndToEnd() {
            User employee = userRepository.findByEmail("user@procurement.com").orElseThrow();
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build(),
                    ProcurementConstraint.builder().attribute("panelType").operator(ConstraintOperator.EQUALS).value("OLED").mandatory(true).build()
            );

            ProcurementRequest request = ProcurementRequest.builder()
                    .user(employee)
                    .category("TV")
                    .quantity(1)
                    .authorizationLimit(new BigDecimal("50000.00"))
                    .status(ProcurementState.SUBMITTED)
                    .build();
            constraints.forEach(request::addConstraint);
            ProcurementRequest saved = procurementRequestRepository.save(request);

            // 1. Initial orchestration escalates to WAITING_APPROVAL
            com.procurement.engine.procurement.model.OrchestrationResultDto step1 =
                    procurementOrchestrator.orchestrate(saved.getId());
            assertThat(step1.getFinalState()).isEqualTo(ProcurementState.WAITING_APPROVAL);

            // 2. Manager approves
            approvalService.approve(saved.getId(),
                    com.procurement.engine.authorization.model.ApprovalActionRequest.ofComments("Budget exception approved for LG OLED"),
                    manager);

            // 3. Verify workflow reaches COMPLETED
            ProcurementRequest completed = procurementRequestRepository.findById(saved.getId()).orElseThrow();
            assertThat(completed.getStatus()).isEqualTo(ProcurementState.COMPLETED);

            // 4. Verify exactly one PurchaseOrder created
            List<com.procurement.engine.purchase.entity.PurchaseOrder> orders = purchaseOrderRepository.findByProcurementId(saved.getId());
            assertThat(orders).hasSize(1);
            assertThat(orders.get(0).getStatus()).isEqualTo(com.procurement.engine.purchase.entity.PurchaseOrderStatus.CONFIRMED);
            assertThat(orders.get(0).getTotalAmount()).isEqualByComparingTo("56000.00");
        }
    }
}
