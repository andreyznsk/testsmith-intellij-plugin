package io.testsmith.plugin.agent.coverage.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoverageSummaryTest {
    @Test
    void coverageRatioReturnsOneForZeroTotalLines() {
        CoverageSummary summary = new CoverageSummary(0, 0, 0);

        assertEquals(1.0, summary.coverageRatio());
    }

    @Test
    void coverageRatioReturnsFractionForNonZeroLines() {
        CoverageSummary summary = new CoverageSummary(10, 4, 6);

        assertEquals(0.4, summary.coverageRatio(), 1e-9);
    }

    @Test
    void invariantsRejectInvalidTotals() {
        assertThrows(IllegalArgumentException.class, () -> new CoverageSummary(5, 3, 1));
    }
}
