package io.testsmith.plugin.agent.coverage.diff;

import io.testsmith.plugin.agent.coverage.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoverageDiffTest {
    @Test
    void deltaReflectsCoverageGrowth() {
        CoverageSnapshot before = snapshotWithCoverage(1, 1);
        CoverageSnapshot after = snapshotWithCoverage(3, 0);

        CoverageDiff diff = new CoverageDiff(before, after);

        assertEquals(2, diff.deltaCoveredLines());
        assertEquals(-1, diff.deltaMissedLines());
        assertTrue(diff.hasProgress());
    }

    @Test
    void hasProgressIsFalseForZeroOrNegativeGrowth() {
        CoverageSnapshot baseline = snapshotWithCoverage(2, 0);
        CoverageSnapshot noChange = snapshotWithCoverage(2, 0);
        CoverageSnapshot regression = snapshotWithCoverage(1, 1);

        assertFalse(new CoverageDiff(baseline, noChange).hasProgress());
        assertFalse(new CoverageDiff(baseline, regression).hasProgress());
    }

    private static CoverageSnapshot snapshotWithCoverage(int coveredLines, int missedLines) {
        int totalLines = coveredLines + missedLines;
        ClassCoverage coverage = new ClassCoverage(
                new ClassId("com.example.Foo"),
                new PackageName("com.example"),
                coveredLines,
                missedLines,
                coveredLines,
                missedLines,
                0,
                0,
                missedLines == 0 ? Set.of() : Set.of(1),
                "Foo.java"
        );

        return new CoverageSnapshot(
                Instant.parse("2024-01-01T00:00:00Z"),
                new CoverageSummary(totalLines, coveredLines, missedLines),
                Map.of(coverage.classId(), coverage)
        );
    }
}
