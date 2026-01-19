package io.testsmith.plugin.agent.coverage.model;

import java.util.Objects;
import java.util.Set;

public record ClassCoverage(
        ClassId classId,
        PackageName packageName,
        int totalLines,
        int coveredLines,
        int missedLines,
        Set<Integer> missedLineNumbers) {
    public ClassCoverage {
        Objects.requireNonNull(classId, "classId must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(missedLineNumbers, "missedLineNumbers must not be null");

        if (totalLines < 0 || coveredLines < 0 || missedLines < 0) {
            throw new IllegalArgumentException("coverage line counts must be non-negative");
        }
        if (coveredLines + missedLines != totalLines) {
            throw new IllegalArgumentException("coveredLines + missedLines must equal totalLines");
        }

        for (Integer missedLine : missedLineNumbers) {
            if (missedLine == null || missedLine <= 0) {
                throw new IllegalArgumentException("missedLineNumbers must contain only positive line numbers");
            }
        }
        Set<Integer> copiedMissedLines = Set.copyOf(missedLineNumbers);
        if (copiedMissedLines.size() != missedLines) {
            throw new IllegalArgumentException("missedLineNumbers size must equal missedLines");
        }
        missedLineNumbers = copiedMissedLines;
    }
}
