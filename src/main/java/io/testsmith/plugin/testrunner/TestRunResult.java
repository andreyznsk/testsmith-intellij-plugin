package io.testsmith.plugin.testrunner;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class TestRunResult {
    private final boolean success;
    private final int exitCode;
    private final Duration duration;
    private final String stdout;
    private final String stderr;
    private final Optional<TestFailureType> failureType;
    private final Optional<String> failureSummary;

    public TestRunResult(
            boolean success,
            int exitCode,
            Duration duration,
            String stdout,
            String stderr,
            Optional<TestFailureType> failureType,
            Optional<String> failureSummary
    ) {
        this.success = success;
        this.exitCode = exitCode;
        this.duration = Objects.requireNonNull(duration, "duration must not be null");
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.failureType = failureType == null ? Optional.empty() : failureType;
        this.failureSummary = failureSummary == null ? Optional.empty() : failureSummary;
    }

    public boolean success() {
        return success;
    }

    public int exitCode() {
        return exitCode;
    }

    public Duration duration() {
        return duration;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }

    public Optional<TestFailureType> failureType() {
        return failureType;
    }

    public Optional<String> failureSummary() {
        return failureSummary;
    }
}
