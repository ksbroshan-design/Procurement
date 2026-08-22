package com.procurement.engine.discovery.source.impl;

import com.procurement.engine.discovery.model.DiscoverySourceResult;
import com.procurement.engine.discovery.model.RawProductCandidate;
import com.procurement.engine.discovery.source.ProductDiscoverySource;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.vendor.entity.Vendor;
import com.procurement.engine.vendor.entity.VendorStatus;
import com.procurement.engine.vendor.repository.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Discovery source for TechDirect Enterprises (Enterprise Direct).
 */
@Component
public class TechDirectProductSource implements ProductDiscoverySource {

    private static final Logger log = LoggerFactory.getLogger(TechDirectProductSource.class);
    public static final String SOURCE_NAME = "TechDirect Enterprise Direct";
    public static final String VENDOR_NAME = "TechDirect Enterprises";

    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;

    public TechDirectProductSource(ProductRepository productRepository, VendorRepository vendorRepository) {
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public DiscoverySourceResult discover(String category, int requestedQuantity) {
        log.debug("Querying [{}] for category [{}]", SOURCE_NAME, category);

        Optional<Vendor> vendorOpt = vendorRepository.findByName(VENDOR_NAME);
        if (vendorOpt.isEmpty()) {
            return DiscoverySourceResult.failure(SOURCE_NAME, "Vendor '" + VENDOR_NAME + "' not registered in catalog.");
        }

        Vendor vendor = vendorOpt.get();
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            return DiscoverySourceResult.failure(SOURCE_NAME, "Vendor '" + VENDOR_NAME + "' is currently " + vendor.getStatus() + ".");
        }

        List<Product> products = productRepository.findByCategoryIgnoreCase(category);
        List<RawProductCandidate> candidates = new ArrayList<>();

        for (Product p : products) {
            if (p.getVendor() != null && p.getVendor().getId().equals(vendor.getId())) {
                candidates.add(RawProductCandidate.builder()
                        .sourceName(SOURCE_NAME)
                        .vendorId(vendor.getId())
                        .vendorName(vendor.getName())
                        .rawId(p.getId().toString())
                        .rawName(p.getName())
                        .rawBrand(p.getBrand())
                        .rawModel(p.getModel())
                        .rawCategory(p.getCategory())
                        .rawPrice(p.getPrice())
                        .rawCurrency(p.getCurrency())
                        .rawAvailability(p.isAvailability())
                        .rawAvailableQuantity(p.getAvailableQuantity())
                        .rawDeliveryDays(p.getDeliveryDays())
                        .rawWarrantyDuration(p.getWarrantyDuration())
                        .rawWarrantyType(p.getWarrantyType())
                        .rawSellerRating(p.getSellerRating())
                        .rawReliabilityScore(p.getReliabilityScore())
                        .rawReturnPolicy(vendor.getReturnPolicy())
                        .rawSpecifications(p.getSpecifications())
                        .build());
            }
        }

        return DiscoverySourceResult.success(SOURCE_NAME, candidates);
    }
}
