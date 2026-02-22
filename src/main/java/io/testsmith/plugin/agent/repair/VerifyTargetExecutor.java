package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.StructuredTest;
import io.testsmith.plugin.testrunner.model.TestExecutionResult;

@FunctionalInterface
public interface VerifyTargetExecutor {
    TestExecutionResult verifyTarget(StructuredTest test);
}
