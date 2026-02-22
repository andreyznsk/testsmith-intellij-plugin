package io.testsmith.plugin.agent.repair;

import java.util.Objects;

public record RepairAttemptLog(
        int attemptNumber,
        RepairFailureType failureType,
        String targetClass,
        String outcome,
        long durationMillis
) {
    public RepairAttemptLog {
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attemptNumber must be > 0");
        }
        Objects.requireNonNull(failureType, "failureType must not be null");
        Objects.requireNonNull(targetClass, "targetClass must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must be >= 0");
        }
    }

    public String format() {
        return "[RepairAttempt #" + attemptNumber + "]" + System.lineSeparator()
                + "FailureType: " + failureType + System.lineSeparator()
                + "TargetClass: " + targetClass + System.lineSeparator()
                + "Outcome: " + outcome + System.lineSeparator()
                + "Duration: " + durationMillis + "ms";
    }
}
