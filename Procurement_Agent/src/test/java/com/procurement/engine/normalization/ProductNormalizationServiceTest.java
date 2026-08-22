package com.procurement.engine.normalization;

import com.procurement.engine.discovery.model.RawProductCandidate;
import com.procurement.engine.normalization.model.NormalizedProductCandidate;
import com.procurement.engine.normalization.service.ProductNormalizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ProductNormalizationServiceTest {

    private ProductNormalizationService normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ProductNormalizationService();
    }

    @Nested
    @DisplayName("Currency and Price Normalization Tests")
    class CurrencyAndPriceTests {

        @Test
        @DisplayName("Parses different currency formats and symbols cleanly")
        void testCurrencyParsing() {
            assertThat(normalizer.normalizePrice("₹55,999")).isEqualByComparingTo("55999.00");
            assertThat(normalizer.normalizePrice("55,999 INR")).isEqualByComparingTo("55999.00");
            assertThat(normalizer.normalizePrice("55999")).isEqualByComparingTo("55999.00");
            assertThat(normalizer.normalizePrice(55999)).isEqualByComparingTo("55999.00");
            assertThat(normalizer.normalizePrice("$1,250.50")).isEqualByComparingTo("1250.50");
            assertThat(normalizer.normalizePrice("€450.00")).isEqualByComparingTo("450.00");
            assertThat(normalizer.normalizePrice(null)).isEqualByComparingTo("0.00");
            assertThat(normalizer.normalizePrice("N/A")).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Normalizes currency string")
        void testCurrencyStringNormalization() {
            assertThat(normalizer.normalizeCurrency("₹")).isEqualTo("INR");
            assertThat(normalizer.normalizeCurrency("INR")).isEqualTo("INR");
            assertThat(normalizer.normalizeCurrency("$")).isEqualTo("USD");
            assertThat(normalizer.normalizeCurrency("USD")).isEqualTo("USD");
            assertThat(normalizer.normalizeCurrency(null)).isEqualTo("INR");
        }
    }

    @Nested
    @DisplayName("Numeric String & Unit Normalization Tests")
    class NumericAndUnitTests {

        @Test
        @DisplayName("Normalizes numeric strings with units into clean numbers")
        void testNumericUnits() {
            assertThat(normalizer.normalizeSpecValue("55 inch")).isEqualTo(55);
            assertThat(normalizer.normalizeSpecValue("55-inch")).isEqualTo(55);
            assertThat(normalizer.normalizeSpecValue("16GB")).isEqualTo(16);
            assertThat(normalizer.normalizeSpecValue("512 GB")).isEqualTo(512);
            assertThat(normalizer.normalizeSpecValue("120 Hz")).isEqualTo(120);
            assertThat(normalizer.normalizeSpecValue("12.6-Inch")).isEqualTo(12.6);
            assertThat(normalizer.normalizeSpecValue("150 kg")).isEqualTo(150);
            assertThat(normalizer.normalizeSpecValue("true")).isEqualTo(Boolean.TRUE);
            assertThat(normalizer.normalizeSpecValue("false")).isEqualTo(Boolean.FALSE);
        }

        @Test
        @DisplayName("Normalizes delivery days and warranty years")
        void testDeliveryAndWarranty() {
            assertThat(normalizer.normalizeInteger("4 days", 7)).isEqualTo(4);
            assertThat(normalizer.normalizeInteger("3", 7)).isEqualTo(3);
            assertThat(normalizer.normalizeWarrantyYears("3 years", 1)).isEqualTo(3);
            assertThat(normalizer.normalizeWarrantyYears("36 months", 1)).isEqualTo(3);
            assertThat(normalizer.normalizeWarrantyYears(2, 1)).isEqualTo(2);
        }

        @Test
        @DisplayName("Normalizes reliability score formats (e.g. 95% -> 0.95, 0.95 -> 0.95)")
        void testReliabilityNormalization() {
            assertThat(normalizer.normalizeReliability("0.95", new BigDecimal("0.80"))).isEqualByComparingTo("0.95");
            assertThat(normalizer.normalizeReliability("95%", new BigDecimal("0.80"))).isEqualByComparingTo("0.95");
            assertThat(normalizer.normalizeReliability(95, new BigDecimal("0.80"))).isEqualByComparingTo("0.95");
        }
    }

    @Nested
    @DisplayName("Dynamic Specifications & Alias Normalization Tests")
    class SpecificationsAndAliasTests {

        @Test
        @DisplayName("Maps vendor field aliases to canonical spec keys while preserving unknown attributes")
        void testSpecificationNormalization() {
            Map<String, Object> rawSpecs = new HashMap<>();
            rawSpecs.put("screen_size", "55 inch");
            rawSpecs.put("display", "OLED");
            rawSpecs.put("refresh_rate", "120Hz");
            rawSpecs.put("customVendorProp", "Enterprise Grade Gold"); // Unknown attribute

            Map<String, Object> normalized = normalizer.normalizeSpecifications(rawSpecs);

            assertThat(normalized).containsEntry("screenSize", 55);
            assertThat(normalized).containsEntry("panelType", "OLED");
            assertThat(normalized).containsEntry("refreshRate", 120);
            // Unknown dynamic specification must be preserved!
            assertThat(normalized).containsEntry("customVendorProp", "Enterprise Grade Gold");
        }

        @Test
        @DisplayName("Normalizes entire RawProductCandidate end-to-end")
        void testEndToEndCandidateNormalization() {
            RawProductCandidate raw = RawProductCandidate.builder()
                    .sourceName("Mock Vendor A")
                    .vendorId(UUID.randomUUID())
                    .vendorName("Vendor A Inc")
                    .rawId(UUID.randomUUID().toString())
                    .rawName("Samsung 55 OLED TV")
                    .rawBrand("Samsung")
                    .rawModel("QA55S90C")
                    .rawCategory("TV")
                    .rawPrice("₹55,999")
                    .rawCurrency("INR")
                    .rawAvailability("AVAILABLE")
                    .rawAvailableQuantity("25 units")
                    .rawDeliveryDays("4 days")
                    .rawWarrantyDuration("3 years")
                    .rawWarrantyType("ONSITE")
                    .rawSellerRating("4.85")
                    .rawReliabilityScore("96%")
                    .rawReturnPolicy("30-day replacement")
                    .rawSpecifications(Map.of("screen_size", "55 inch", "resolution", "4K", "panel", "OLED"))
                    .build();

            NormalizedProductCandidate candidate = normalizer.normalize(raw);

            assertThat(candidate.getName()).isEqualTo("Samsung 55 OLED TV");
            assertThat(candidate.getPrice()).isEqualByComparingTo("55999.00");
            assertThat(candidate.getCurrency()).isEqualTo("INR");
            assertThat(candidate.isAvailability()).isTrue();
            assertThat(candidate.getAvailableQuantity()).isEqualTo(25);
            assertThat(candidate.getDeliveryDays()).isEqualTo(4);
            assertThat(candidate.getWarrantyDuration()).isEqualTo(3);
            assertThat(candidate.getSellerRating()).isEqualByComparingTo("4.85");
            assertThat(candidate.getReliabilityScore()).isEqualByComparingTo("0.96");
            assertThat(candidate.getSpecifications()).containsEntry("screenSize", 55);
            assertThat(candidate.getSpecifications()).containsEntry("panelType", "OLED");
            assertThat(candidate.getSpecifications()).containsEntry("resolution", "4K");
        }

        @Test
        @DisplayName("Safe handling of null, missing and malformed candidates")
        void testNullAndMalformedSafety() {
            assertThatCode(() -> {
                NormalizedProductCandidate candidate = normalizer.normalize(new RawProductCandidate(
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
                ));
                assertThat(candidate).isNotNull();
                assertThat(candidate.getName()).isEqualTo("Unnamed Product");
                assertThat(candidate.getPrice()).isEqualByComparingTo("0.00");
            }).doesNotThrowAnyException();
        }
    }
}
