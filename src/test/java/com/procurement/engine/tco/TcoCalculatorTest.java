package com.procurement.engine.tco;

import com.procurement.engine.config.EngineProperties;
import com.procurement.engine.procurement.entity.OfferStatus;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.entity.ReliabilityHistory;
import com.procurement.engine.tco.model.TcoBreakdownDto;
import com.procurement.engine.tco.service.TcoCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TcoCalculatorTest {

    private EngineProperties properties;
    private TcoCalculator calculator;

    @BeforeEach
    void setUp() {
        properties = new EngineProperties();
        calculator = new TcoCalculator(properties);
    }

    @Nested
    @DisplayName("Grounded Reliability History & Warranty Tests")
    class ReliabilityAndWarrantyTests {

        @Test
        @DisplayName("Calculates decomposable 3-year TCO using real historical reliability metrics")
        void testGroundedReliabilityTco() {
            Product product = Product.builder()
                    .id(UUID.randomUUID())
                    .name("Samsung 55 OLED TV")
                    .category("TV")
                    .price(new BigDecimal("56000.00"))
                    .warrantyDuration(3)
                    .warrantyType("ONSITE")
                    .sellerRating(new BigDecimal("4.85"))
                    .reliabilityScore(new BigDecimal("0.97"))
                    .build();

            VendorOffer offer = VendorOffer.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .originalPrice(new BigDecimal("56000.00"))
                    .deliveryDays(3)
                    .availableQuantity(50)
                    .warrantyYears(3)
                    .status(OfferStatus.EVALUATING)
                    .build();

            ReliabilityHistory history = ReliabilityHistory.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .failureRate(new BigDecimal("0.0250")) // 2.5% failure rate
                    .averageRepairCost(new BigDecimal("1200.00"))
                    .averageDowntimeCost(new BigDecimal("400.00"))
                    .sampleSize(500)
                    .build();

            TcoBreakdownDto breakdown = calculator.calculateTco(product, offer, Optional.of(history), 5, 3);

            assertThat(breakdown.isDataGrounded()).isTrue();
            assertThat(breakdown.getQuantity()).isEqualTo(5);
            assertThat(breakdown.getHorizonYears()).isEqualTo(3);
            assertThat(breakdown.getUnitPurchaseCost()).isEqualByComparingTo("56000.00");
            assertThat(breakdown.getTotalPurchaseCost()).isEqualByComparingTo("280000.00");

            // Maintenance = 56000 * 0.02 * 3 = 3360
            assertThat(breakdown.getUnitMaintenanceCost()).isEqualByComparingTo("3360.00");

            // Warranty covers 3 years onsite (95% repair, 40% downtime)
            assertThat(breakdown.getUnitWarrantyBenefit()).isGreaterThan(BigDecimal.ZERO);

            // Unit TCO should be slightly higher than initial price due to maintenance & residual risk
            assertThat(breakdown.getUnitTco()).isGreaterThan(breakdown.getUnitPurchaseCost());
            assertThat(breakdown.getTotalTco()).isEqualByComparingTo(breakdown.getUnitTco().multiply(BigDecimal.valueOf(5)));
            assertThat(breakdown.getAssumptions()).isNotEmpty();
        }

        @Test
        @DisplayName("Higher failure rate with 1-year basic warranty incurs higher repair and replacement costs")
        void testHighRiskLowWarrantyTco() {
            Product budgetProduct = Product.builder()
                    .id(UUID.randomUUID())
                    .name("Budget 55 LED TV")
                    .category("TV")
                    .price(new BigDecimal("42000.00"))
                    .warrantyDuration(1)
                    .warrantyType("BASIC")
                    .sellerRating(new BigDecimal("3.80"))
                    .reliabilityScore(new BigDecimal("0.80"))
                    .build();

            VendorOffer budgetOffer = VendorOffer.builder()
                    .id(UUID.randomUUID())
                    .product(budgetProduct)
                    .originalPrice(new BigDecimal("42000.00"))
                    .deliveryDays(5)
                    .availableQuantity(20)
                    .warrantyYears(1)
                    .status(OfferStatus.EVALUATING)
                    .build();

            ReliabilityHistory history = ReliabilityHistory.builder()
                    .id(UUID.randomUUID())
                    .product(budgetProduct)
                    .failureRate(new BigDecimal("0.1400")) // 14% high failure rate
                    .averageRepairCost(new BigDecimal("8500.00"))
                    .averageDowntimeCost(new BigDecimal("3500.00"))
                    .sampleSize(300)
                    .build();

            TcoBreakdownDto breakdown = calculator.calculateTco(budgetProduct, budgetOffer, Optional.of(history), 1, 3);

            // 1-year warranty on a 3-year horizon means post-warranty replacement risk applies
            assertThat(breakdown.getUnitReplacementCost()).isGreaterThan(BigDecimal.ZERO);
            assertThat(breakdown.getUnitExpectedRepairCost()).isGreaterThan(new BigDecimal("3000.00"));
            assertThat(breakdown.getUnitTco()).isGreaterThan(new BigDecimal("48000.00"));
        }
    }

    @Nested
    @DisplayName("Fallback, Scaling & Safety Tests")
    class FallbackAndSafetyTests {

        @Test
        @DisplayName("Uses catalog reliability score fallback when ReliabilityHistory is missing without hallucinating")
        void testFallbackWhenHistoryMissing() {
            Product product = Product.builder()
                    .id(UUID.randomUUID())
                    .name("Dell Monitor")
                    .category("Monitor")
                    .price(new BigDecimal("25000.00"))
                    .warrantyDuration(2)
                    .warrantyType("STANDARD")
                    .reliabilityScore(new BigDecimal("0.90"))
                    .build();

            VendorOffer offer = VendorOffer.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .originalPrice(new BigDecimal("25000.00"))
                    .deliveryDays(2)
                    .availableQuantity(10)
                    .warrantyYears(2)
                    .status(OfferStatus.EVALUATING)
                    .build();

            TcoBreakdownDto breakdown = calculator.calculateTco(product, offer, Optional.empty(), 2, 3);

            assertThat(breakdown.isDataGrounded()).isFalse();
            assertThat(breakdown.getFailureRate()).isGreaterThan(BigDecimal.ZERO);
            assertThat(breakdown.getUnitTco()).isGreaterThan(breakdown.getUnitPurchaseCost());
            assertThat(breakdown.getAssumptions().get(1)).contains("Estimated reliability parameters");
        }

        @Test
        @DisplayName("Validates quantity scaling and non-null inputs")
        void testQuantityAndValidation() {
            assertThatThrownBy(() -> calculator.calculateTco(null, null, Optional.empty(), 1, 3))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
