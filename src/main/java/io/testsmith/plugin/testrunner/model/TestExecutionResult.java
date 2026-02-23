package io.testsmith.plugin.testrunner.model;

import io.testsmith.plugin.testrunner.failure.FailureReport;

import java.time.Duration;
import java.util.Objects;

public record TestExecutionResult(
        TestExecutionPhase phase,
        TestExecutionStatus status,
        TestFailureKind failureKind,
        String failedTest,
        String failureMessage,
        FailureReport failureReport,
        String stdout,
        String stderr,
        Duration duration
) {
    public TestExecutionResult {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        if (status == TestExecutionStatus.SUCCESS) {
            if (failureKind != null || failedTest != null || failureMessage != null || failureReport != null) {
                throw new IllegalArgumentException("Success results must not include failure details");
            }
        }
    }

    public boolean isSuccess() {
        return status == TestExecutionStatus.SUCCESS;
    }

    public boolean isFixableByLlm() {
        return status == TestExecutionStatus.TEST_FAILED
                || status == TestExecutionStatus.COMPILATION_FAILED;
    }

    public boolean isInfrastructureProblem() {
        return status == TestExecutionStatus.INFRASTRUCTURE_ERROR
                || status == TestExecutionStatus.TIMEOUT;
    }
}
