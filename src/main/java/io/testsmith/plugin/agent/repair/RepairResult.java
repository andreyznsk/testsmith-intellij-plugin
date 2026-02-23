package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.StructuredTest;

import java.util.Objects;

public record RepairResult(
        RepairOutcome outcome,
        StructuredTest repairedTest,
        String message
) {
    public RepairResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        message = message == null ? "" : message;

        if (outcome == RepairOutcome.REPAIRED && repairedTest == null) {
            throw new IllegalArgumentException("repairedTest must be provided when outcome=REPAIRED");
        }
        if (outcome != RepairOutcome.REPAIRED && repairedTest != null) {
            throw new IllegalArgumentException("repairedTest must be null when outcome is not REPAIRED");
        }
    }

    public static RepairResult repaired(StructuredTest repairedTest, String message) {
        return new RepairResult(RepairOutcome.REPAIRED, repairedTest, message);
    }

    public static RepairResult rejected(String message) {
        return new RepairResult(RepairOutcome.REJECTED, null, message);
    }

    public static RepairResult hardAbort(String message) {
        return new RepairResult(RepairOutcome.HARD_ABORT, null, message);
    }
}
