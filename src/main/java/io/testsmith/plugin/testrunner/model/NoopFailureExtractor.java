package io.testsmith.plugin.testrunner.model;

import java.util.Objects;

public final class NoopFailureExtractor implements FailureExtractor {
    @Override
    public TestExecutionResult enrich(TestExecutionResult raw) {
        return Objects.requireNonNull(raw, "raw must not be null");
    }
}
