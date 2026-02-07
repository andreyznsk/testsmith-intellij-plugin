package io.testsmith.plugin.agent.stagnation;

import java.util.Objects;

/**
 * Context passed to stagnation evaluation for a single iteration.
 */
public record AgentIterationContext(StagnationTuning tuning) {
    public AgentIterationContext {
        Objects.requireNonNull(tuning, "tuning must not be null");
    }
}
