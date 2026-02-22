package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.testrunner.model.TestExecutionPhase;
import io.testsmith.plugin.testrunner.model.TestExecutionResult;
import io.testsmith.plugin.testrunner.model.TestExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepairFailureClassifierTest {
    private final RepairFailureClassifier classifier = new RepairFailureClassifier();

    @Test
    void classifiesMissingImportAsRepairable() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.COMPILATION_FAILED,
                null,
                null,
                "cannot find symbol\nimport com.example.Missing",
                null,
                "",
                "cannot find symbol\nimport com.example.Missing",
                Duration.ofMillis(50)
        );

        assertEquals(RepairFailureType.MISSING_IMPORT, classifier.classify(result));
    }

    @Test
    void classifiesAssertionFailure() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.TEST_FAILED,
                null,
                null,
                "org.opentest4j.AssertionFailedError: expected:<1> but was:<2>",
                null,
                "",
                "",
                Duration.ofMillis(50)
        );

        assertEquals(RepairFailureType.ASSERTION, classifier.classify(result));
    }

    @Test
    void classifiesMissingJacocoAsInfrastructure() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.FULL_SUITE_COVERAGE,
                TestExecutionStatus.INFRASTRUCTURE_ERROR,
                null,
                null,
                "JaCoCo XML not found at build/reports/jacoco/test/jacocoTestReport.xml",
                null,
                "",
                "",
                Duration.ofMillis(50)
        );

        assertEquals(RepairFailureType.MISSING_JACOCO_XML, classifier.classify(result));
    }
}
