package com.procurement.engine.normalization.service;

import com.procurement.engine.discovery.model.RawProductCandidate;
import com.procurement.engine.normalization.model.NormalizedProductCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Product-agnostic normalization service.
 * <p>
 * Converts heterogeneous vendor raw product payloads into canonical {@link NormalizedProductCandidate}
 * structures. Standardizes monetary fields, numeric units, booleans, and key aliases while
 * faithfully preserving dynamic domain specifications.
 */
@Service
public class ProductNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(ProductNormalizationService.class);

    private static final Pattern LEADING_NUMERIC_PATTERN = Pattern.compile("^([+-]?\\d+(\\.\\d+)?|\\.\\d+)");
    private static final Pattern CURRENCY_SYMBOL_PATTERN = Pattern.compile("[₹$€£¥,INR|USD|EUR|GBP|JPY|AUD|CAD|CHF]");

    // Canonical key alias mappings (normalized lowercase without separators -> canonical spec key)
    private static final Map<String, String> SPEC_KEY_ALIASES = Map.ofEntries(
            Map.entry("screensize", "screenSize"),
            Map.entry("screen", "screenSize"),
            Map.entry("displaysize", "screenSize"),
            Map.entry("paneltype", "panelType"),
            Map.entry("panel", "panelType"),
            Map.entry("display", "panelType"),
            Map.entry("refreshrate", "refreshRate"),
            Map.entry("refresh", "refreshRate"),
            Map.entry("smarttv", "smartTv"),
            Map.entry("hdmiports", "hdmiPorts"),
            Map.entry("ramsize", "ram"),
            Map.entry("memory", "ram"),
            Map.entry("storagecapacity", "storage"),
            Map.entry("disk", "storage"),
            Map.entry("ssd", "storage"),
            Map.entry("processorname", "processor"),
            Map.entry("cpu", "processor"),
            Map.entry("batterycapacity", "batteryCapacity"),
            Map.entry("batteryhours", "batteryHours"),
            Map.entry("weightkg", "weightKg"),
            Map.entry("weightcapacitykg", "weightCapacityKg"),
            Map.entry("weightcapacity", "weightCapacityKg"),
            Map.entry("lumbarsupport", "lumbarSupport"),
            Map.entry("adjustablearmrests", "adjustableArmrests"),
            Map.entry("tiltlock", "tiltLock"),
            Map.entry("switchtype", "switchType"),
            Map.entry("hotswappable", "hotSwappable"),
            Map.entry("powerdeliverywatts", "powerDeliveryWatts")
    );

    /**
     * Normalizes a raw product candidate into canonical form.
     */
    public NormalizedProductCandidate normalize(RawProductCandidate raw) {
        if (raw == null) {
            return null;
        }

        // 1. Core Identifiers & Names
        String name = normalizeString(firstNonNull(raw.getRawName(), "Unnamed Product"));
        String brand = normalizeString(raw.getRawBrand());
        String model = normalizeString(raw.getRawModel());
        String category = normalizeString(raw.getRawCategory());

        // 2. Price and Currency
        BigDecimal price = normalizePrice(raw.getRawPrice());
        String currency = normalizeCurrency(raw.getRawCurrency());

        // 3. Availability and Quantity
        boolean availability = normalizeBoolean(raw.getRawAvailability(), true);
        int availableQuantity = normalizeInteger(raw.getRawAvailableQuantity(), 0);

        // 4. Delivery & Logistics
        int deliveryDays = normalizeInteger(raw.getRawDeliveryDays(), 7);

        // 5. Warranty & Ratings
        int warrantyDuration = normalizeWarrantyYears(raw.getRawWarrantyDuration(), 1);
        String warrantyType = normalizeString(firstNonNull(raw.getRawWarrantyType(), "STANDARD"));
        BigDecimal sellerRating = normalizeDecimal(raw.getRawSellerRating(), new BigDecimal("4.00"));
        BigDecimal reliabilityScore = normalizeReliability(raw.getRawReliabilityScore(), new BigDecimal("0.80"));
        String returnPolicy = normalizeString(raw.getRawReturnPolicy());

        // 6. Dynamic Specifications Normalization
        Map<String, Object> normalizedSpecs = normalizeSpecifications(raw.getRawSpecifications());

        // Check if raw name/title or extra fields contained top-level aliases that belong in specs
        return NormalizedProductCandidate.builder()
                .sourceName(raw.getSourceName())
                .vendorId(raw.getVendorId())
                .vendorName(raw.getVendorName())
                .rawId(raw.getRawId())
                .name(name)
                .category(category)
                .brand(brand)
                .model(model)
                .price(price)
                .currency(currency)
                .availability(availability)
                .availableQuantity(availableQuantity)
                .deliveryDays(deliveryDays)
                .warrantyDuration(warrantyDuration)
                .warrantyType(warrantyType)
                .sellerRating(sellerRating)
                .reliabilityScore(reliabilityScore)
                .returnPolicy(returnPolicy)
                .specifications(normalizedSpecs)
                .build();
    }

    /**
     * Normalizes a batch of raw product candidates.
     */
    public List<NormalizedProductCandidate> normalizeAll(List<RawProductCandidate> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<NormalizedProductCandidate> result = new ArrayList<>(rawList.size());
        for (RawProductCandidate raw : rawList) {
            NormalizedProductCandidate normalized = normalize(raw);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    /**
     * Standardizes dynamic specification key-value pairs.
     */
    public Map<String, Object> normalizeSpecifications(Map<String, Object> rawSpecs) {
        if (rawSpecs == null || rawSpecs.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> normalized = new HashMap<>();

        for (Map.Entry<String, Object> entry : rawSpecs.entrySet()) {
            String rawKey = entry.getKey();
            Object rawValue = entry.getValue();

            if (rawKey == null || rawKey.isBlank() || rawValue == null) {
                continue;
            }

            String cleanKey = cleanKey(rawKey);
            String canonicalKey = SPEC_KEY_ALIASES.getOrDefault(cleanKey, rawKey);

            Object normalizedValue = normalizeSpecValue(rawValue);
            normalized.put(canonicalKey, normalizedValue);
        }

        return normalized;
    }

    private static final Pattern PURE_NUMERIC_PATTERN = Pattern.compile("^[+-]?(\\d+(\\.\\d+)?|\\.\\d+)$");
    private static final Pattern KNOWN_UNIT_PATTERN = Pattern.compile("^[+-]?(\\d+(\\.\\d+)?|\\.\\d+)[\\s\\-_]*(inch|inches|\"|'|gb|mb|tb|kb|hz|khz|mhz|ghz|w|watts|kg|kgs|lbs|mah|days?|hours?|hrs?|years?|yrs?|months?)$", Pattern.CASE_INSENSITIVE);

    /**
     * Normalizes a specification value (e.g. "55 inch" -> 55, "16GB" -> 16, "true" -> true).
     */
    public Object normalizeSpecValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }

        if (value instanceof Collection<?> coll) {
            List<Object> normalizedList = new ArrayList<>();
            for (Object item : coll) {
                normalizedList.add(normalizeSpecValue(item));
            }
            return normalizedList;
        }

        String str = value.toString().trim();
        if (str.isEmpty()) {
            return str;
        }

        // Check boolean
        if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("yes")) {
            return Boolean.TRUE;
        }
        if (str.equalsIgnoreCase("false") || str.equalsIgnoreCase("no")) {
            return Boolean.FALSE;
        }

        // Pure numeric string (e.g. "55", "12.6")
        if (PURE_NUMERIC_PATTERN.matcher(str).matches()) {
            try {
                if (str.contains(".")) {
                    return Double.parseDouble(str);
                } else {
                    return Integer.parseInt(str);
                }
            } catch (Exception ignored) {}
        }

        // Numeric string with recognized unit (e.g. "55 inch", "16GB", "120Hz", "150kg", "10200 mAh")
        Matcher unitMatcher = KNOWN_UNIT_PATTERN.matcher(str);
        if (unitMatcher.matches()) {
            String numStr = unitMatcher.group(1);
            try {
                if (numStr.contains(".")) {
                    return Double.parseDouble(numStr);
                } else {
                    return Integer.parseInt(numStr);
                }
            } catch (Exception ignored) {}
        }

        return str;
    }

    /**
     * Parses monetary values into BigDecimal (handling ₹55,999, 55999 INR, $450.50, commas, etc.).
     */
    public BigDecimal normalizePrice(Object priceObj) {
        if (priceObj == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (priceObj instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }
        if (priceObj instanceof Number num) {
            return new BigDecimal(num.toString()).setScale(2, RoundingMode.HALF_UP);
        }

        String str = priceObj.toString().trim();
        // Remove currency symbols, commas, and currency codes
        String clean = CURRENCY_SYMBOL_PATTERN.matcher(str).replaceAll("").trim();

        Matcher matcher = LEADING_NUMERIC_PATTERN.matcher(clean);
        if (matcher.find()) {
            try {
                return new BigDecimal(matcher.group(1)).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception e) {
                log.warn("Could not parse price from string: {}", priceObj);
            }
        }

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public String normalizeCurrency(String rawCurrency) {
        if (rawCurrency == null || rawCurrency.isBlank()) {
            return "INR";
        }
        String upper = rawCurrency.trim().toUpperCase();
        if (upper.contains("INR") || upper.contains("₹") || upper.contains("RUPEE")) {
            return "INR";
        }
        if (upper.contains("USD") || upper.contains("$") || upper.contains("DOLLAR")) {
            return "USD";
        }
        if (upper.contains("EUR") || upper.contains("€") || upper.contains("EURO")) {
            return "EUR";
        }
        return upper;
    }

    public boolean normalizeBoolean(Object boolObj, boolean defaultValue) {
        if (boolObj == null) {
            return defaultValue;
        }
        if (boolObj instanceof Boolean b) {
            return b;
        }
        String str = boolObj.toString().trim().toLowerCase();
        if (str.equals("true") || str.equals("yes") || str.equals("1") || str.equals("available") || str.equals("in_stock")) {
            return true;
        }
        if (str.equals("false") || str.equals("no") || str.equals("0") || str.equals("unavailable") || str.equals("out_of_stock")) {
            return false;
        }
        return defaultValue;
    }

    public int normalizeInteger(Object intObj, int defaultValue) {
        if (intObj == null) {
            return defaultValue;
        }
        if (intObj instanceof Number num) {
            return num.intValue();
        }
        String str = intObj.toString().trim();
        Matcher matcher = LEADING_NUMERIC_PATTERN.matcher(str);
        if (matcher.find()) {
            try {
                return (int) Double.parseDouble(matcher.group(1));
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    public int normalizeWarrantyYears(Object warrantyObj, int defaultValue) {
        if (warrantyObj == null) {
            return defaultValue;
        }
        if (warrantyObj instanceof Number num) {
            return num.intValue();
        }
        String str = warrantyObj.toString().trim().toLowerCase();
        Matcher matcher = LEADING_NUMERIC_PATTERN.matcher(str);
        if (matcher.find()) {
            try {
                int val = (int) Double.parseDouble(matcher.group(1));
                if (str.contains("month")) {
                    return Math.max(1, val / 12);
                }
                return val;
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    public BigDecimal normalizeDecimal(Object decObj, BigDecimal defaultValue) {
        if (decObj == null) {
            return defaultValue;
        }
        if (decObj instanceof BigDecimal bd) {
            return bd;
        }
        if (decObj instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        String str = decObj.toString().trim();
        Matcher matcher = LEADING_NUMERIC_PATTERN.matcher(str);
        if (matcher.find()) {
            try {
                return new BigDecimal(matcher.group(1));
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    public BigDecimal normalizeReliability(Object relObj, BigDecimal defaultValue) {
        if (relObj == null) {
            return defaultValue;
        }
        BigDecimal val = normalizeDecimal(relObj, defaultValue);
        // If reliability is represented as 95 or 95% (greater than 1.0), normalize to 0.95
        if (val.compareTo(BigDecimal.ONE) > 0) {
            val = val.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        return val;
    }

    private String normalizeString(Object strObj) {
        if (strObj == null) {
            return null;
        }
        String str = strObj.toString().trim();
        return str.isEmpty() ? null : str;
    }

    private String cleanKey(String key) {
        return key.toLowerCase().replaceAll("[_\\-\\s]", "");
    }

    private String firstNonNull(String val, String defaultVal) {
        return (val != null && !val.isBlank()) ? val : defaultVal;
    }
}
