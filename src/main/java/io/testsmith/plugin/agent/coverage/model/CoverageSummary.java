package io.testsmith.plugin.agent.coverage.model;

public record CoverageSummary(long totalLines, long coveredLines, long missedLines) {
    public CoverageSummary {
        if (totalLines < 0 || coveredLines < 0 || missedLines < 0) {
            throw new IllegalArgumentException("coverage line counts must be non-negative");
        }
        if (coveredLines + missedLines != totalLines) {
            throw new IllegalArgumentException("coveredLines + missedLines must equal totalLines");
        }
    }

    public double coverageRatio() {
        if (totalLines == 0) {
            return 1.0;
        }
        return coveredLines / (double) totalLines;
    }
}
