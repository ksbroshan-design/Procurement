package com.procurement.engine.constraint.resolver;

import com.procurement.engine.product.entity.Product;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Controlled resolver for product attributes.
 * <p>
 * Evaluates authoritative top-level Product fields first to ensure integrity,
 * and falls back to dynamic Product.specifications (JSONB) fields.
 * Does NOT use unrestricted reflection.
 */
@Component
public class ProductAttributeResolver {

    /**
     * Resolves an attribute value from the given product.
     *
     * @param product       The product entity
     * @param rawAttribute  The attribute name to resolve
     * @return Optional containing the resolved value, or empty if attribute is not present
     */
    public Optional<Object> resolve(Product product, String rawAttribute) {
        if (product == null || rawAttribute == null || rawAttribute.isBlank()) {
            return Optional.empty();
        }

        String normalizedKey = normalize(rawAttribute);

        // 1. Authoritative top-level Product properties take strict precedence
        switch (normalizedKey) {
            case "price":
                return Optional.ofNullable(product.getPrice());
            case "brand":
                return Optional.ofNullable(product.getBrand());
            case "model":
                return Optional.ofNullable(product.getModel());
            case "name":
                return Optional.ofNullable(product.getName());
            case "category":
                return Optional.ofNullable(product.getCategory());
            case "currency":
                return Optional.ofNullable(product.getCurrency());
            case "deliverydays":
            case "deliverytime":
                return Optional.of(product.getDeliveryDays());
            case "warrantyduration":
            case "warranty":
            case "warrantyyears":
                return Optional.of(product.getWarrantyDuration());
            case "warrantytype":
                return Optional.ofNullable(product.getWarrantyType());
            case "sellerrating":
            case "rating":
                return Optional.ofNullable(product.getSellerRating());
            case "reliabilityscore":
            case "reliability":
                return Optional.ofNullable(product.getReliabilityScore());
            case "returnwindow":
            case "returnpolicydays":
                return Optional.of(product.getReturnWindow());
            case "availability":
            case "isavailable":
                return Optional.of(product.isAvailability());
            case "availablequantity":
            case "quantity":
            case "stock":
            case "stockquantity":
                return Optional.of(product.getAvailableQuantity());
            default:
                break;
        }

        // 2. Fall back to dynamic Product.specifications (JSONB)
        Map<String, Object> specs = product.getSpecifications();
        if (specs == null || specs.isEmpty()) {
            return Optional.empty();
        }

        // Exact match
        if (specs.containsKey(rawAttribute)) {
            return Optional.ofNullable(specs.get(rawAttribute));
        }

        // Case-insensitive or normalized key match in specifications
        for (Map.Entry<String, Object> entry : specs.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(rawAttribute) || normalize(entry.getKey()).equals(normalizedKey)) {
                return Optional.ofNullable(entry.getValue());
            }
        }

        return Optional.empty();
    }

    /**
     * Checks whether an attribute exists on the product.
     */
    public boolean exists(Product product, String rawAttribute) {
        return resolve(product, rawAttribute).isPresent();
    }

    private String normalize(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase().replaceAll("[_\\-\\s]", "");
    }
}
