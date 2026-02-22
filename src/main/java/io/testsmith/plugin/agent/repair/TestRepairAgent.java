package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.StructuredTest;
import io.testsmith.plugin.testrunner.model.TestExecutionResult;

public interface TestRepairAgent {
    RepairResult attemptRepair(
            StructuredTest originalTest,
            TestExecutionResult failure,
            RepairContext context
    );
}
