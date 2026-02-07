package io.testsmith.plugin.agent.stagnation;

import io.testsmith.plugin.agent.coverage.diff.CoverageDiff;
import io.testsmith.plugin.agent.coverage.model.ClassCoverage;

import java.util.Optional;

/**
 * Pure stagnation detection policy: no I/O, no PSI access, deterministic behavior.
 */
public interface StagnationGuard {
    StagnationDecision evaluate(
            AgentIterationContext context,
            StagnationState state,
            ClassCoverage candidate,
            Optional<CoverageDiff> lastFullSuiteDiff);
}
