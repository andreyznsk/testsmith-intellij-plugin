package io.testsmith.plugin.agent.stagnation;

import java.util.Objects;

/**
 * Result of a stagnation evaluation. Reason and details are meaningful only when stagnating.
 */
public final class StagnationDecision {
    private static final StagnationDecision NOT_STAGNATING = new StagnationDecision(false, null, "");

    private final boolean stagnating;
    private final StagnationReason reason;
    private final String details;

    private StagnationDecision(boolean stagnating, StagnationReason reason, String details) {
        this.stagnating = stagnating;
        this.reason = reason;
        this.details = details == null ? "" : details;
    }

    public static StagnationDecision notStagnating() {
        return NOT_STAGNATING;
    }

    public static StagnationDecision stagnating(StagnationReason reason, String details) {
        Objects.requireNonNull(reason, "reason must not be null");
        return new StagnationDecision(true, reason, details);
    }

    public boolean isStagnating() {
        return stagnating;
    }

    public StagnationReason reason() {
        if (!stagnating) {
            throw new IllegalStateException("Not stagnating");
        }
        return reason;
    }

    public String details() {
        if (!stagnating) {
            throw new IllegalStateException("Not stagnating");
        }
        return details;
    }
}
