package io.testsmith.plugin.agent.stagnation;

/**
 * Thresholds for triggering stagnation detection.
 */
public record StagnationTuning(
        int maxNoProgressFullSuiteRuns,
        int maxSameClassRepeats) {
    public StagnationTuning {
        if (maxNoProgressFullSuiteRuns < 1) {
            throw new IllegalArgumentException("maxNoProgressFullSuiteRuns must be at least 1");
        }
        if (maxSameClassRepeats < 1) {
            throw new IllegalArgumentException("maxSameClassRepeats must be at least 1");
        }
    }
}
