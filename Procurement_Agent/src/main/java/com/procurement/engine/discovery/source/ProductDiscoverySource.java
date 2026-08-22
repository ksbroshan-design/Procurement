package com.procurement.engine.discovery.source;

import com.procurement.engine.discovery.model.DiscoverySourceResult;

/**
 * Service Provider Interface (SPI) for product discovery sources.
 * <p>
 * Allows plugging in different vendor sources (e.g. enterprise direct, wholesale B2B,
 * authorized partners, marketplace feeds) without modifying the core procurement engine.
 */
public interface ProductDiscoverySource {

    /**
     * Unique identifier / name of this discovery source.
     */
    String getSourceName();

    /**
     * Whether this source is currently enabled and active.
     */
    boolean isEnabled();

    /**
     * Discovers raw product candidates matching the given category and quantity requirement.
     *
     * @param category          The canonical or requested category name
     * @param requestedQuantity The quantity required by procurement
     * @return DiscoverySourceResult containing candidate products or error message
     */
    DiscoverySourceResult discover(String category, int requestedQuantity);
}
