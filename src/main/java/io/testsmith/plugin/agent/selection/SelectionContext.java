package io.testsmith.plugin.agent.selection;

import java.util.Objects;

public record SelectionContext(
        ExclusionRules exclusions,
        SelectionTuning tuning,
        SelectionState state) {
    public SelectionContext {
        Objects.requireNonNull(exclusions, "exclusions must not be null");
        Objects.requireNonNull(tuning, "tuning must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }
}
