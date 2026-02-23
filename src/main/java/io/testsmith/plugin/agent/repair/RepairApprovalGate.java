package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.StructuredTest;

@FunctionalInterface
public interface RepairApprovalGate {
    boolean approve(
            StructuredTest original,
            StructuredTest repaired,
            String diff,
            int attemptNumber
    );

    static RepairApprovalGate alwaysApprove() {
        return (original, repaired, diff, attemptNumber) -> true;
    }
}
