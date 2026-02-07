package io.testsmith.plugin.testrunner.failure;

public interface FailureExtractor {
    FailureReport extract(String stdout, String stderr, int exitCode, boolean timedOut);
}
