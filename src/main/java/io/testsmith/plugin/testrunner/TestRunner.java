package io.testsmith.plugin.testrunner;

import io.testsmith.plugin.testrunner.model.TestExecutionResult;

public interface TestRunner {
    TestExecutionResult run(TestRunRequest request);
}
