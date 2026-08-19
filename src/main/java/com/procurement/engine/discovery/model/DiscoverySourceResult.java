package com.procurement.engine.discovery.model;

import java.util.Collections;
import java.util.List;

/**
 * Result returned by a single ProductDiscoverySource query.
 */
public class DiscoverySourceResult {

    private final String sourceName;
    private final boolean success;
    private final List<RawProductCandidate> candidates;
    private final String errorMessage;

    public DiscoverySourceResult(String sourceName, boolean success, List<RawProductCandidate> candidates, String errorMessage) {
        this.sourceName = sourceName;
        this.success = success;
        this.candidates = candidates != null ? List.copyOf(candidates) : Collections.emptyList();
        this.errorMessage = errorMessage;
    }

    public static DiscoverySourceResult success(String sourceName, List<RawProductCandidate> candidates) {
        return new DiscoverySourceResult(sourceName, true, candidates, null);
    }

    public static DiscoverySourceResult failure(String sourceName, String errorMessage) {
        return new DiscoverySourceResult(sourceName, false, Collections.emptyList(), errorMessage);
    }

    public String getSourceName() { return sourceName; }
    public boolean isSuccess() { return success; }
    public List<RawProductCandidate> getCandidates() { return candidates; }
    public String getErrorMessage() { return errorMessage; }
}
