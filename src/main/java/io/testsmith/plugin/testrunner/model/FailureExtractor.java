package io.testsmith.plugin.testrunner.model;

public interface FailureExtractor {
    TestExecutionResult enrich(TestExecutionResult raw);
}
