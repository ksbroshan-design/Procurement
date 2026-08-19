package com.procurement.engine.discovery.model;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated discovery and constraint evaluation result for a procurement request.
 */
public class DiscoveryResult {

    private final UUID procurementId;
    private final String category;
    private final List<String> sourcesQueried;
    private final int rawCandidatesCount;
    private final int normalizedCandidatesCount;
    private final int eligibleCandidatesCount;
    private final int rejectedCandidatesCount;
    private final List<CandidateOfferDto> eligibleOffers;
    private final List<RejectionDiagnosticDto> rejectedOffers;
    private final List<SourceFailureDto> sourceFailures;
    private final String status;
    private final String message;

    public DiscoveryResult(UUID procurementId,
                           String category,
                           List<String> sourcesQueried,
                           int rawCandidatesCount,
                           int normalizedCandidatesCount,
                           int eligibleCandidatesCount,
                           int rejectedCandidatesCount,
                           List<CandidateOfferDto> eligibleOffers,
                           List<RejectionDiagnosticDto> rejectedOffers,
                           List<SourceFailureDto> sourceFailures,
                           String status,
                           String message) {
        this.procurementId = procurementId;
        this.category = category;
        this.sourcesQueried = sourcesQueried != null ? List.copyOf(sourcesQueried) : Collections.emptyList();
        this.rawCandidatesCount = rawCandidatesCount;
        this.normalizedCandidatesCount = normalizedCandidatesCount;
        this.eligibleCandidatesCount = eligibleCandidatesCount;
        this.rejectedCandidatesCount = rejectedCandidatesCount;
        this.eligibleOffers = eligibleOffers != null ? List.copyOf(eligibleOffers) : Collections.emptyList();
        this.rejectedOffers = rejectedOffers != null ? List.copyOf(rejectedOffers) : Collections.emptyList();
        this.sourceFailures = sourceFailures != null ? List.copyOf(sourceFailures) : Collections.emptyList();
        this.status = status;
        this.message = message;
    }

    public UUID getProcurementId() { return procurementId; }
    public String getCategory() { return category; }
    public List<String> getSourcesQueried() { return sourcesQueried; }
    public int getRawCandidatesCount() { return rawCandidatesCount; }
    public int getNormalizedCandidatesCount() { return normalizedCandidatesCount; }
    public int getEligibleCandidatesCount() { return eligibleCandidatesCount; }
    public int getRejectedCandidatesCount() { return rejectedCandidatesCount; }
    public List<CandidateOfferDto> getEligibleOffers() { return eligibleOffers; }
    public List<RejectionDiagnosticDto> getRejectedOffers() { return rejectedOffers; }
    public List<SourceFailureDto> getSourceFailures() { return sourceFailures; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
}
