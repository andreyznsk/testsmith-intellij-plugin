package io.testsmith.plugin.agent.stagnation;

/**
 * Reason for detecting stagnation inside the agent loop.
 */
public enum StagnationReason {
    NO_COVERAGE_PROGRESS,
    SAME_CLASS_THRASHING
}
