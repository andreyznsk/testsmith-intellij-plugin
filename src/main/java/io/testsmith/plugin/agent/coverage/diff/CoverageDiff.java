package io.testsmith.plugin.agent.coverage.diff;

import io.testsmith.plugin.agent.coverage.model.CoverageSnapshot;

import java.util.Objects;

public record CoverageDiff(CoverageSnapshot before, CoverageSnapshot after) {
    public CoverageDiff {
        Objects.requireNonNull(before, "before snapshot must not be null");
        Objects.requireNonNull(after, "after snapshot must not be null");
    }

    public long deltaCoveredLines() {
        return after.summary().coveredLines() - before.summary().coveredLines();
    }

    public long deltaMissedLines() {
        return after.summary().missedLines() - before.summary().missedLines();
    }

    public boolean hasProgress() {
        return deltaCoveredLines() > 0;
    }
}
