package com.procurement.engine.constraint;

import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.constraint.model.ConstraintStatus;
import com.procurement.engine.constraint.model.ProductConstraintEvaluation;
import com.procurement.engine.constraint.model.SingleConstraintResult;
import com.procurement.engine.constraint.resolver.ProductAttributeResolver;
import com.procurement.engine.constraint.service.ConstraintEvaluator;
import com.procurement.engine.product.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ConstraintEvaluatorTest {

    private ConstraintEvaluator evaluator;
    private ProductAttributeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProductAttributeResolver();
        evaluator = new ConstraintEvaluator(resolver);
    }

    private Product createProduct(String name, String category, BigDecimal price, int deliveryDays, Map<String, Object> specs) {
        return Product.builder()
                .id(UUID.randomUUID())
                .name(name)
                .category(category)
                .brand("TestBrand")
                .model("ModelX")
                .price(price)
                .currency("INR")
                .availability(true)
                .availableQuantity(50)
                .deliveryDays(deliveryDays)
                .sellerRating(new BigDecimal("4.80"))
                .reliabilityScore(new BigDecimal("0.95"))
                .warrantyDuration(3)
                .warrantyType("ONSITE")
                .returnWindow(30)
                .specifications(new HashMap<>(specs))
                .build();
    }

    @Nested
    @DisplayName("Attribute Resolution & Precedence Tests")
    class AttributeResolutionTests {

        @Test
        @DisplayName("Authoritative top-level price is resolved correctly")
        void testTopLevelPriceResolution() {
            Product product = createProduct("Test TV", "TV", new BigDecimal("55000.00"), 3, Map.of("screenSize", 55));
            ProcurementConstraint constraint = ProcurementConstraint.builder()
                    .attribute("price")
                    .operator(ConstraintOperator.LESS_THAN_OR_EQUAL)
                    .value("60000")
                    .mandatory(true)
                    .build();

            SingleConstraintResult result = evaluator.evaluateSingleConstraint(product, constraint);
            assertThat(result.getStatus()).isEqualTo(ConstraintStatus.PASS);
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("Authoritative top-level deliveryDays is resolved correctly")
        void testTopLevelDeliveryDaysResolution() {
            Product product = createProduct("Test Laptop", "Laptop", new BigDecimal("75000.00"), 4, Map.of("ram", 16));
            ProcurementConstraint constraint = ProcurementConstraint.builder()
                    .attribute("deliveryDays")
                    .operator(ConstraintOperator.LESS_THAN_OR_EQUAL)
                    .value("5")
                    .mandatory(true)
                    .build();

            SingleConstraintResult result = evaluator.evaluateSingleConstraint(product, constraint);
            assertThat(result.getStatus()).isEqualTo(ConstraintStatus.PASS);
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("Authoritative top-level field takes precedence over JSONB duplicate attribute")
        void testTopLevelPrecedenceOverJsonbDuplicate() {
            // Product authoritative price is 50000, but specifications maliciously contains "price": 30000
            Map<String, Object> specs = new HashMap<>();
            specs.put("price", 30000);
            specs.put("screenSize", 55);

            Product product = createProduct("Precedence Test TV", "TV", new BigDecimal("50000.00"), 3, specs);

            // Constraint: price <= 40000 (should FAIL against authoritative 50000, NOT pass against 30000)
            ProcurementConstraint constraint = ProcurementConstraint.builder()
                    .attribute("price")
                    .operator(ConstraintOperator.LESS_THAN_OR_EQUAL)
                    .value("40000")
                    .mandatory(true)
                    .build();

            SingleConstraintResult result = evaluator.evaluateSingleConstraint(product, constraint);
            assertThat(result.getStatus()).isEqualTo(ConstraintStatus.FAIL);
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getActualValue()).isEqualTo(new BigDecimal("50000.00"));
        }

        @Test
        @DisplayName("Dynamic JSONB specifications attribute is resolved when not in top-level fields")
        void testJsonbAttributeResolution() {
            Product product = createProduct("Office Chair", "Office chair", new BigDecimal("15000.00"), 5,
                    Map.of("material", "Mesh", "weightCapacityKg", 150, "lumbarSupport", true));

            ProcurementConstraint constraint = ProcurementConstraint.builder()
                    .attribute("material")
                    .operator(ConstraintOperator.EQUALS)
                    .value("Mesh")
                    .mandatory(true)
                    .build();

            SingleConstraintResult result = evaluator.evaluateSingleConstraint(product, constraint);
            assertThat(result.getStatus()).isEqualTo(ConstraintStatus.PASS);
            assertThat(result.isPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Operator Semantics Tests")
    class OperatorTests {

        @Test
        @DisplayName("EQUALS (=) works for strings, numbers, booleans")
        void testEqualsOperator() {
            Product product = createProduct("Laptop", "Laptop", new BigDecimal("80000.00"), 3,
                    Map.of("ram", 16, "touchscreen", true, "panelType", "OLED"));

            // String equality (case-insensitive)
            ProcurementConstraint c1 = ProcurementConstraint.builder()
                    .attribute("panelType").operator(ConstraintOperator.EQUALS).value("oled").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c1).getStatus()).isEqualTo(ConstraintStatus.PASS);

            // Numeric equality
            ProcurementConstraint c2 = ProcurementConstraint.builder()
                    .attribute("ram").operator(ConstraintOperator.EQUALS).value("16").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c2).getStatus()).isEqualTo(ConstraintStatus.PASS);

            // Boolean equality
            ProcurementConstraint c3 = ProcurementConstraint.builder()
                    .attribute("touchscreen").operator(ConstraintOperator.EQUALS).value("true").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c3).getStatus()).isEqualTo(ConstraintStatus.PASS);
        }

        @Test
        @DisplayName("NOT_EQUALS (!=) works correctly")
        void testNotEqualsOperator() {
            Product product = createProduct("Chair", "Chair", new BigDecimal("5000.00"), 3, Map.of("material", "Plastic"));

            ProcurementConstraint c1 = ProcurementConstraint.builder()
                    .attribute("material").operator(ConstraintOperator.NOT_EQUALS).value("Leather").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c1).getStatus()).isEqualTo(ConstraintStatus.PASS);

            ProcurementConstraint c2 = ProcurementConstraint.builder()
                    .attribute("material").operator(ConstraintOperator.NOT_EQUALS).value("Plastic").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c2).getStatus()).isEqualTo(ConstraintStatus.FAIL);
        }

        @Test
        @DisplayName("GREATER_THAN (>) and GREATER_THAN_OR_EQUAL (>=)")
        void testGreaterOperators() {
            Product product = createProduct("TV", "TV", new BigDecimal("50000.00"), 3, Map.of("screenSize", 55));

            // > 50 (PASS)
            ProcurementConstraint c1 = ProcurementConstraint.builder()
                    .attribute("screenSize").operator(ConstraintOperator.GREATER_THAN).value("50").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c1).getStatus()).isEqualTo(ConstraintStatus.PASS);

            // > 55 (FAIL)
            ProcurementConstraint c2 = ProcurementConstraint.builder()
                    .attribute("screenSize").operator(ConstraintOperator.GREATER_THAN).value("55").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c2).getStatus()).isEqualTo(ConstraintStatus.FAIL);

            // >= 55 (PASS)
            ProcurementConstraint c3 = ProcurementConstraint.builder()
                    .attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c3).getStatus()).isEqualTo(ConstraintStatus.PASS);
        }

        @Test
        @DisplayName("LESS_THAN (<) and LESS_THAN_OR_EQUAL (<=)")
        void testLessOperators() {
            Product product = createProduct("Keyboard", "Keyboard", new BigDecimal("4500.00"), 2, Map.of("batteryHours", 40));

            // < 50 (PASS)
            ProcurementConstraint c1 = ProcurementConstraint.builder()
                    .attribute("batteryHours").operator(ConstraintOperator.LESS_THAN).value("50").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c1).getStatus()).isEqualTo(ConstraintStatus.PASS);

            // < 40 (FAIL)
            ProcurementConstraint c2 = ProcurementConstraint.builder()
                    .attribute("batteryHours").operator(ConstraintOperator.LESS_THAN).value("40").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c2).getStatus()).isEqualTo(ConstraintStatus.FAIL);

            // <= 40 (PASS)
            ProcurementConstraint c3 = ProcurementConstraint.builder()
                    .attribute("batteryHours").operator(ConstraintOperator.LESS_THAN_OR_EQUAL).value("40").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c3).getStatus()).isEqualTo(ConstraintStatus.PASS);
        }

        @Test
        @DisplayName("IN operator works for comma-separated list and JSON arrays")
        void testInOperator() {
            Product product = createProduct("Monitor", "Monitor", new BigDecimal("35000.00"), 2,
                    Map.of("resolution", "4K", "refreshRate", 144));

            // Comma separated
            ProcurementConstraint c1 = ProcurementConstraint.builder()
                    .attribute("resolution").operator(ConstraintOperator.IN).value("QHD, 4K, 8K").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c1).getStatus()).isEqualTo(ConstraintStatus.PASS);

            // Not in list
            ProcurementConstraint c2 = ProcurementConstraint.builder()
                    .attribute("resolution").operator(ConstraintOperator.IN).value("FHD, HD").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c2).getStatus()).isEqualTo(ConstraintStatus.FAIL);

            // JSON array of numbers
            ProcurementConstraint c3 = ProcurementConstraint.builder()
                    .attribute("refreshRate").operator(ConstraintOperator.IN).value("[60, 120, 144]").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c3).getStatus()).isEqualTo(ConstraintStatus.PASS);
        }

        @Test
        @DisplayName("CONTAINS operator works for strings and collections")
        void testContainsOperator() {
            Product product = createProduct("Keyboard", "Keyboard", new BigDecimal("8000.00"), 3,
                    Map.of("connectivity", "Wireless/Bluetooth/Type-C", "supportedOs", List.of("Windows", "macOS", "Linux")));

            // String contains substring
            ProcurementConstraint c1 = ProcurementConstraint.builder()
                    .attribute("connectivity").operator(ConstraintOperator.CONTAINS).value("Bluetooth").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c1).getStatus()).isEqualTo(ConstraintStatus.PASS);

            ProcurementConstraint c2 = ProcurementConstraint.builder()
                    .attribute("connectivity").operator(ConstraintOperator.CONTAINS).value("Solar").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c2).getStatus()).isEqualTo(ConstraintStatus.FAIL);

            // Collection contains element
            ProcurementConstraint c3 = ProcurementConstraint.builder()
                    .attribute("supportedOs").operator(ConstraintOperator.CONTAINS).value("macOS").mandatory(true).build();
            assertThat(evaluator.evaluateSingleConstraint(product, c3).getStatus()).isEqualTo(ConstraintStatus.PASS);
        }
    }

    @Nested
    @DisplayName("Deterministic Missing Attribute & Type Mismatch Tests")
    class MissingAttributeAndTypeMismatchTests {

        @Test
        @DisplayName("Missing mandatory attribute produces FAIL and makes product ineligible")
        void testMissingMandatoryAttribute() {
            Product product = createProduct("Basic TV", "TV", new BigDecimal("40000.00"), 3, Map.of("screenSize", 55));
            ProcurementConstraint mandatoryConstraint = ProcurementConstraint.builder()
                    .attribute("hdrSupport")
                    .operator(ConstraintOperator.EQUALS)
                    .value("HDR10+")
                    .mandatory(true)
                    .weight(new BigDecimal("1.00"))
                    .build();

            ProductConstraintEvaluation eval = evaluator.evaluate(product, List.of(mandatoryConstraint));
            assertThat(eval.isEligible()).isFalse();
            assertThat(eval.getHardFailureCount()).isEqualTo(1);
            assertThat(eval.getConstraintResults().get(0).getStatus()).isEqualTo(ConstraintStatus.FAIL);
        }

        @Test
        @DisplayName("Missing optional attribute produces UNKNOWN, product remains eligible with penalty")
        void testMissingOptionalAttribute() {
            Product product = createProduct("Basic TV", "TV", new BigDecimal("40000.00"), 3, Map.of("screenSize", 55));
            ProcurementConstraint optionalConstraint = ProcurementConstraint.builder()
                    .attribute("hdrSupport")
                    .operator(ConstraintOperator.EQUALS)
                    .value("HDR10+")
                    .mandatory(false)
                    .weight(new BigDecimal("0.75"))
                    .build();

            ProductConstraintEvaluation eval = evaluator.evaluate(product, List.of(optionalConstraint));
            assertThat(eval.isEligible()).isTrue();
            assertThat(eval.getHardFailureCount()).isEqualTo(0);
            assertThat(eval.getSoftFailureCount()).isEqualTo(1);
            assertThat(eval.getTotalPenalty()).isEqualByComparingTo("0.75");
            assertThat(eval.getConstraintResults().get(0).getStatus()).isEqualTo(ConstraintStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Mandatory type mismatch produces FAIL and makes product ineligible")
        void testMandatoryTypeMismatch() {
            Product product = createProduct("Test Chair", "Chair", new BigDecimal("12000.00"), 4,
                    Map.of("material", "Mesh")); // String instead of number for '>'
            ProcurementConstraint mandatoryMismatch = ProcurementConstraint.builder()
                    .attribute("material")
                    .operator(ConstraintOperator.GREATER_THAN)
                    .value("100")
                    .mandatory(true)
                    .build();

            ProductConstraintEvaluation eval = evaluator.evaluate(product, List.of(mandatoryMismatch));
            assertThat(eval.isEligible()).isFalse();
            assertThat(eval.getHardFailureCount()).isEqualTo(1);
            assertThat(eval.getConstraintResults().get(0).getStatus()).isEqualTo(ConstraintStatus.FAIL);
        }

        @Test
        @DisplayName("Optional type mismatch produces UNKNOWN, product remains eligible with penalty")
        void testOptionalTypeMismatch() {
            Product product = createProduct("Test Chair", "Chair", new BigDecimal("12000.00"), 4,
                    Map.of("material", "Mesh")); // String instead of number for '>'
            ProcurementConstraint optionalMismatch = ProcurementConstraint.builder()
                    .attribute("material")
                    .operator(ConstraintOperator.GREATER_THAN)
                    .value("100")
                    .mandatory(false)
                    .weight(new BigDecimal("0.50"))
                    .build();

            ProductConstraintEvaluation eval = evaluator.evaluate(product, List.of(optionalMismatch));
            assertThat(eval.isEligible()).isTrue();
            assertThat(eval.getHardFailureCount()).isEqualTo(0);
            assertThat(eval.getSoftFailureCount()).isEqualTo(1);
            assertThat(eval.getTotalPenalty()).isEqualByComparingTo("0.50");
            assertThat(eval.getConstraintResults().get(0).getStatus()).isEqualTo(ConstraintStatus.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("Mixed Constraints & Product-Agnostic Scenarios")
    class MixedConstraintTests {

        @Test
        @DisplayName("Mixed hard and soft constraints evaluate eligibility and penalty independently")
        void testMixedHardAndSoftConstraints() {
            Product tv = createProduct("LG OLED C3", "TV", new BigDecimal("56000.00"), 3,
                    Map.of("screenSize", 55, "resolution", "4K", "panelType", "OLED", "refreshRate", 120));

            List<ProcurementConstraint> constraints = List.of(
                    // Hard 1: screenSize >= 55 (PASS)
                    ProcurementConstraint.builder().attribute("screenSize").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("55").mandatory(true).build(),
                    // Hard 2: resolution = 4K (PASS)
                    ProcurementConstraint.builder().attribute("resolution").operator(ConstraintOperator.EQUALS).value("4K").mandatory(true).build(),
                    // Hard 3: price <= 60000 (PASS)
                    ProcurementConstraint.builder().attribute("price").operator(ConstraintOperator.LESS_THAN_OR_EQUAL).value("60000").mandatory(true).build(),
                    // Soft 1: panelType = OLED (PASS)
                    ProcurementConstraint.builder().attribute("panelType").operator(ConstraintOperator.EQUALS).value("OLED").mandatory(false).weight(new BigDecimal("0.80")).build(),
                    // Soft 2: refreshRate >= 144 (FAIL soft -> penalty 0.50)
                    ProcurementConstraint.builder().attribute("refreshRate").operator(ConstraintOperator.GREATER_THAN_OR_EQUAL).value("144").mandatory(false).weight(new BigDecimal("0.50")).build()
            );

            ProductConstraintEvaluation eval = evaluator.evaluate(tv, constraints);
            assertThat(eval.isEligible()).isTrue();
            assertThat(eval.getPassedCount()).isEqualTo(4);
            assertThat(eval.getHardFailureCount()).isEqualTo(0);
            assertThat(eval.getSoftFailureCount()).isEqualTo(1);
            assertThat(eval.getTotalPenalty()).isEqualByComparingTo("0.50");
        }

        @Test
        @DisplayName("Robustness check: null safety does not throw NullPointerException")
        void testNullSafety() {
            Product product = createProduct("Empty Spec Product", "General", null, 0, Map.of());
            ProcurementConstraint c1 = ProcurementConstraint.builder()
                    .attribute("nonExistent")
                    .operator(ConstraintOperator.EQUALS)
                    .value(null)
                    .mandatory(false)
                    .build();

            assertThatCode(() -> evaluator.evaluate(product, List.of(c1))).doesNotThrowAnyException();
        }
    }
}
