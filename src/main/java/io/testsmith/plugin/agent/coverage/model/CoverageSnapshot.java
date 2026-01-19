package io.testsmith.plugin.agent.coverage.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record CoverageSnapshot(
        Instant timestamp,
        CoverageSummary summary,
        Map<ClassId, ClassCoverage> classes) {
    public CoverageSnapshot {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(classes, "classes must not be null");

        for (Map.Entry<ClassId, ClassCoverage> entry : classes.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("classes must not contain null keys");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("classes must not contain null values");
            }
        }

        Map<ClassId, ClassCoverage> copiedClasses = Map.copyOf(classes);
        validateConsistency(summary, copiedClasses);
        classes = copiedClasses;
    }

    private static void validateConsistency(CoverageSummary summary, Map<ClassId, ClassCoverage> classes) {
        int total = 0;
        int covered = 0;
        int missed = 0;
        for (ClassCoverage coverage : classes.values()) {
            total += coverage.totalLines();
            covered += coverage.coveredLines();
            missed += coverage.missedLines();
        }
        if (summary.totalLines() != total || summary.coveredLines() != covered || summary.missedLines() != missed) {
            // We validate in the constructor to keep snapshots self-consistent. If profiling ever
            // shows this aggregation as a hot path for huge snapshots, it can be moved to a separate
            // validator to keep construction cheap while retaining an explicit consistency check.
            throw new IllegalArgumentException("summary must match the aggregate of class coverage values");
        }
    }
}
