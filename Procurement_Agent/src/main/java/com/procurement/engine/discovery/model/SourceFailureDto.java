package com.procurement.engine.discovery.model;

import java.time.Instant;

/**
 * Diagnostic record when a discovery source fails or encounters issues.
 */
public class SourceFailureDto {

    private final String sourceName;
    private final String error;
    private final Instant timestamp;

    public SourceFailureDto(String sourceName, String error, Instant timestamp) {
        this.sourceName = sourceName;
        this.error = error;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static SourceFailureDto of(String sourceName, String error) {
        return new SourceFailureDto(sourceName, error, Instant.now());
    }

    public String getSourceName() { return sourceName; }
    public String getError() { return error; }
    public Instant getTimestamp() { return timestamp; }
}
