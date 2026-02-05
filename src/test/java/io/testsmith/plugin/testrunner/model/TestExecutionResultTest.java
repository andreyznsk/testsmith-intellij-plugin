package io.testsmith.plugin.testrunner.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TestExecutionResultTest {

    @Test
    void successConvenienceMethods() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.SUCCESS,
                null,
                null,
                null,
                null,
                "out",
                "err",
                Duration.ofSeconds(1)
        );

        assertTrue(result.isSuccess());
        assertFalse(result.isFixableByLlm());
        assertFalse(result.isInfrastructureProblem());
    }

    @Test
    void failureConvenienceMethods() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.TEST_FAILED,
                null,
                null,
                "boom",
                null,
                "out",
                "err",
                Duration.ofSeconds(1)
        );

        assertFalse(result.isSuccess());
        assertTrue(result.isFixableByLlm());
        assertFalse(result.isInfrastructureProblem());
    }

    @Test
    void infrastructureConvenienceMethods() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.TIMEOUT,
                null,
                null,
                "timeout",
                null,
                "out",
                "err",
                Duration.ofSeconds(1)
        );

        assertFalse(result.isSuccess());
        assertFalse(result.isFixableByLlm());
        assertTrue(result.isInfrastructureProblem());
    }

    @Test
    void successMustNotContainFailureDetails() {
        assertThrows(IllegalArgumentException.class, () -> new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.SUCCESS,
                null,
                null,
                "should be null",
                null,
                "out",
                "err",
                Duration.ofSeconds(1)
        ));
    }

    @Test
    void stdoutAndStderrDefaultToEmpty() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.TEST_FAILED,
                null,
                null,
                "boom",
                null,
                null,
                null,
                Duration.ofSeconds(1)
        );

        assertEquals("", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void durationIsRequired() {
        assertThrows(NullPointerException.class, () -> new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.TEST_FAILED,
                null,
                null,
                "boom",
                null,
                "out",
                "err",
                null
        ));
    }
}
