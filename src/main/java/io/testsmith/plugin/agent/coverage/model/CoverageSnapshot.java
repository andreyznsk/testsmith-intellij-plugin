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
        long total = 0L;
        long covered = 0L;
        long missed = 0L;

        for (ClassCoverage c : classes.values()) {
            if (c.lineCovered() < 0 || c.lineMissed() < 0) {
                continue;
            }
            total += c.totalLines();
            covered += c.lineCovered();
            missed += c.lineMissed();
        }

        // Overflow guard: we keep public API as int, so snapshot must fit into int range.
        if (total > Integer.MAX_VALUE || covered > Integer.MAX_VALUE || missed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("coverage totals exceed int range");
        }

        int totalInt = (int) total;
        int coveredInt = (int) covered;
        int missedInt = (int) missed;

        if (summary.totalLines() != totalInt
                || summary.coveredLines() != coveredInt
                || summary.missedLines() != missedInt) {
            throw new IllegalArgumentException("summary must match the aggregate of class coverage values");
        }
    }
}
