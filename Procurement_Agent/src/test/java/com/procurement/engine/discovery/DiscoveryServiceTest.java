package com.procurement.engine.discovery;

import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.model.CandidateOfferDto;
import com.procurement.engine.discovery.model.DiscoveryResult;
import com.procurement.engine.discovery.model.DiscoverySourceResult;
import com.procurement.engine.discovery.model.RejectionDiagnosticDto;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.discovery.source.ProductDiscoverySource;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
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
class DiscoveryServiceTest {

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private List<ProductDiscoverySource> discoverySources;

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

    @Nested
    @DisplayName("Multi-Source Discovery & Vendor Filtering Tests")
    class MultiSourceTests {

        @Test
        @DisplayName("Queries multiple discovery sources and filters out suspended vendor")
        void testMultiSourceDiscoveryExcludesSuspendedVendor() {
            assertThat(discoverySources).hasSizeGreaterThanOrEqualTo(4);

            // Hard constraint: Screen size >= 55
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder()
                            .attribute("screenSize")
                            .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                            .value("55")
                            .mandatory(true)
                            .build()
            );

            ProcurementRequest request = createProcurement("TV", 5, new BigDecimal("350000.00"), constraints);

            DiscoveryResult result = discoveryService.discoverAndEvaluate(request.getId());

            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getSourcesQueried()).hasSizeGreaterThanOrEqualTo(4);
            assertThat(result.getEligibleCandidatesCount()).isGreaterThan(0);

            // PrimeGoods Distribution is suspended -> must be recorded in failures / excluded from eligible
            assertThat(result.getSourceFailures()).anyMatch(f -> f.getSourceName().contains("PrimeGoods") || f.getError().contains("UNAVAILABLE"));
            assertThat(result.getEligibleOffers()).noneMatch(o -> o.getVendorName().contains("PrimeGoods"));

            // Request state transitioned to EVALUATING
            ProcurementRequest updated = procurementRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProcurementState.EVALUATING);
        }

        @Test
        @DisplayName("Preserves duplicate product models as distinct vendor offers")
        void testDuplicateProductOfferPreservation() {
            // Monitor category: multiple vendors offer 27-inch monitors
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder()
                            .attribute("screenSize")
                            .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                            .value("27")
                            .mandatory(true)
                            .build()
            );

            ProcurementRequest request = createProcurement("Monitor", 10, new BigDecimal("600000.00"), constraints);
            DiscoveryResult result = discoveryService.discoverAndEvaluate(request.getId());

            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getEligibleCandidatesCount()).isGreaterThanOrEqualTo(3);

            // Verify offers come from multiple distinct vendors
            long distinctVendors = result.getEligibleOffers().stream()
                    .map(CandidateOfferDto::getVendorName)
                    .distinct()
                    .count();
            assertThat(distinctVendors).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Constraint Integration & Rejection Diagnostics Tests")
    class ConstraintIntegrationTests {

        @Test
        @DisplayName("Separates eligible products from rejected products with structured failure diagnostics")
        void testRejectionDiagnostics() {
            // TV category with tight budget: price <= 50000 and screenSize >= 55
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

            ProcurementRequest request = createProcurement("TV", 5, new BigDecimal("250000.00"), constraints);
            DiscoveryResult result = discoveryService.discoverAndEvaluate(request.getId());

            assertThat(result.getEligibleCandidatesCount()).isGreaterThan(0);
            assertThat(result.getRejectedCandidatesCount()).isGreaterThan(0);

            // Check rejection diagnostics details
            List<RejectionDiagnosticDto> rejections = result.getRejectedOffers();
            assertThat(rejections).isNotEmpty();
            RejectionDiagnosticDto firstRejection = rejections.get(0);
            assertThat(firstRejection.getFailedConstraints()).isNotEmpty();
            assertThat(firstRejection.getFailedConstraints().get(0).getAttribute()).isNotNull();
            assertThat(firstRejection.getFailedConstraints().get(0).getReason()).isNotNull();
        }

        @Test
        @DisplayName("Handles case where all discovered products fail hard constraints (NO_ELIGIBLE_PRODUCTS)")
        void testAllProductsFailHardConstraints() {
            // Impossibly high constraint: RAM >= 128GB
            List<ProcurementConstraint> constraints = List.of(
                    ProcurementConstraint.builder()
                            .attribute("ram")
                            .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                            .value("128")
                            .mandatory(true)
                            .build()
            );

            ProcurementRequest request = createProcurement("Laptop", 5, new BigDecimal("500000.00"), constraints);
            DiscoveryResult result = discoveryService.discoverAndEvaluate(request.getId());

            assertThat(result.getStatus()).isEqualTo("NO_ELIGIBLE_PRODUCTS");
            assertThat(result.getEligibleCandidatesCount()).isEqualTo(0);
            assertThat(result.getRejectedCandidatesCount()).isGreaterThan(0);
            assertThat(result.getRejectedOffers()).allMatch(r -> !r.getFailedConstraints().isEmpty());
        }

        @Test
        @DisplayName("Handles non-existent category (NO_DISCOVERY_RESULTS)")
        void testNonExistentCategory() {
            ProcurementRequest request = createProcurement("Submarine", 1, new BigDecimal("1000000.00"), List.of());
            DiscoveryResult result = discoveryService.discoverAndEvaluate(request.getId());

            assertThat(result.getStatus()).isEqualTo("NO_DISCOVERY_RESULTS");
            assertThat(result.getEligibleCandidatesCount()).isEqualTo(0);
            assertThat(result.getRawCandidatesCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Product-Agnostic Engine Coverage Across All 6 Categories")
    class AllCategoriesTests {

        @Test
        @DisplayName("Works seamlessly for Laptop")
        void testLaptopDiscovery() {
            ProcurementRequest req = createProcurement("Laptop", 5, new BigDecimal("500000.00"), List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
            ));
            DiscoveryResult res = discoveryService.discoverAndEvaluate(req.getId());
            assertThat(res.getEligibleCandidatesCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Works seamlessly for Tablet")
        void testTabletDiscovery() {
            ProcurementRequest req = createProcurement("Tablet", 5, new BigDecimal("250000.00"), List.of(
                    ProcurementConstraint.builder().attribute("storage").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("128").mandatory(true).build()
            ));
            DiscoveryResult res = discoveryService.discoverAndEvaluate(req.getId());
            assertThat(res.getEligibleCandidatesCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Works seamlessly for Office Chair")
        void testOfficeChairDiscovery() {
            ProcurementRequest req = createProcurement("Office chair", 5, new BigDecimal("250000.00"), List.of(
                    ProcurementConstraint.builder().attribute("lumbarSupport").operator(ConstraintOperator.EQUALS).value("true").mandatory(true).build()
            ));
            DiscoveryResult res = discoveryService.discoverAndEvaluate(req.getId());
            assertThat(res.getEligibleCandidatesCount()).isGreaterThan(0);
        }
        @Test
        @DisplayName("Works seamlessly for Keyboard")
        void testKeyboardDiscovery() {
            ProcurementRequest req = createProcurement("Keyboard", 10, new BigDecimal("150000.00"), List.of(
                    ProcurementConstraint.builder().attribute("price").operator(ConstraintOperator.LESS_THAN_OR_EQUAL).value("15000").mandatory(true).build()
            ));
            DiscoveryResult res = discoveryService.discoverAndEvaluate(req.getId());
            assertThat(res.getEligibleCandidatesCount()).isGreaterThan(0);
        }
    }

    @Autowired
    private com.procurement.engine.product.repository.ProductRepository productRepository;

    @Nested
    @DisplayName("Stock-Aware Candidate Eligibility Tests")
    class StockAwareDiscoveryTests {

        @Test
        @DisplayName("Eligible product with sufficient stock is placed in eligibleOffers (Pool A)")
        void testEligibleWithSufficientStockIncludedInPoolA() {
            ProcurementRequest request = createProcurement("Laptop", 5, new BigDecimal("500000.00"), List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
            ));

            DiscoveryResult result = discoveryService.discoverAndEvaluate(request.getId());

            assertThat(result.getEligibleCandidatesCount()).isGreaterThan(0);
            assertThat(result.getEligibleOffers()).allMatch(o -> o.getAvailableQuantity() >= 5);
        }

        @Test
        @DisplayName("Product satisfying constraints but with insufficient stock is rejected from Pool A")
        void testEligibleWithInsufficientStockExcludedFromPoolA() {
            // Request quantity 100 which exceeds available stock of all individual products (max 40)
            ProcurementRequest request = createProcurement("Laptop", 100, new BigDecimal("10000000.00"), List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
            ));

            DiscoveryResult result = discoveryService.discoverAndEvaluate(request.getId());

            assertThat(result.getEligibleCandidatesCount()).isEqualTo(0);
            assertThat(result.getRejectedOffers()).isNotEmpty();
            assertThat(result.getRejectedOffers()).allMatch(r -> r.getFailedConstraints().stream()
                    .anyMatch(fc -> "availableQuantity".equals(fc.getAttribute())));
        }

        @Test
        @DisplayName("Product satisfying constraints but with availability=false is rejected from Pool A")
        void testEligibleWithAvailabilityFalseExcludedFromPoolA() {
            List<com.procurement.engine.product.entity.Product> laptops = productRepository.findByCategoryIgnoreCase("Laptop");
            for (com.procurement.engine.product.entity.Product p : laptops) {
                if ("Lenovo ThinkPad T14s Gen 4".equals(p.getName())) {
                    p.setAvailability(false);
                    productRepository.save(p);
                }
            }

            ProcurementRequest request = createProcurement("Laptop", 5, new BigDecimal("500000.00"), List.of(
                    ProcurementConstraint.builder().attribute("ram").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("16").mandatory(true).build()
            ));

            DiscoveryResult result = discoveryService.discoverAndEvaluate(request.getId());

            assertThat(result.getEligibleOffers()).noneMatch(o -> "Lenovo ThinkPad T14s Gen 4".equals(o.getProductName()));
            assertThat(result.getRejectedOffers()).anyMatch(r -> "Lenovo ThinkPad T14s Gen 4".equals(r.getProductName()));
        }

        @Test
        @DisplayName("When all candidate offers are out of stock, returns NO_ELIGIBLE_PRODUCTS")
        void testAllOffersOutOfStockReturnsNoEligibleProducts() {
            List<com.procurement.engine.product.entity.Product> laptops = productRepository.findByCategoryIgnoreCase("Laptop");
            for (com.procurement.engine.product.entity.Product p : laptops) {
                p.setAvailableQuantity(0);
                p.setAvailability(false);
                productRepository.save(p);
            }

            ProcurementRequest request = createProcurement("Laptop", 1, new BigDecimal("500000.00"), List.of());

            DiscoveryResult result = discoveryService.discoverAndEvaluate(request.getId());

            assertThat(result.getStatus()).isEqualTo("NO_ELIGIBLE_PRODUCTS");
            assertThat(result.getEligibleCandidatesCount()).isEqualTo(0);
            assertThat(result.getRejectedCandidatesCount()).isGreaterThan(0);
        }
    }
}
