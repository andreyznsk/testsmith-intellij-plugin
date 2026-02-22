package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.StructuredTest;
import io.testsmith.plugin.testrunner.model.TestExecutionResult;

import java.util.Objects;

public record FixLoopResult(
        FixLoopStatus status,
        StructuredTest finalTest,
        TestExecutionResult finalExecution,
        int attemptsUsed,
        String message
) {
    public FixLoopResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(finalTest, "finalTest must not be null");
        Objects.requireNonNull(finalExecution, "finalExecution must not be null");
        if (attemptsUsed < 0 || attemptsUsed > RepairPolicy.MAX_REPAIR_ATTEMPTS) {
            throw new IllegalArgumentException("attemptsUsed is out of allowed range");
        }
        message = message == null ? "" : message;
    }
}
