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
                "error: package com.example.missing does not exist\nimport com.example.missing.Foo;",
                null,
                "",
                "error: package com.example.missing does not exist\nimport com.example.missing.Foo;",
                Duration.ofMillis(50)
        );

        assertEquals(RepairFailureType.COMPILATION_MISSING_IMPORT, classifier.classify(result));
    }

    @Test
    void prefersImportSignalWhenCompilationContainsMultipleHints() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.COMPILATION_FAILED,
                null,
                null,
                "error: package com.example.missing does not exist\n"
                        + "import com.example.missing.Foo;\n"
                        + "cannot find symbol\n"
                        + "symbol: class Foo",
                null,
                "",
                "error: package com.example.missing does not exist\n"
                        + "import com.example.missing.Foo;\n"
                        + "cannot find symbol\n"
                        + "symbol: class Foo",
                Duration.ofMillis(50)
        );

        assertEquals(RepairFailureType.COMPILATION_MISSING_IMPORT, classifier.classify(result));
    }

    @Test
    void classifiesMissingType() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.COMPILATION_FAILED,
                null,
                null,
                "cannot find symbol\nsymbol: class MissingType",
                null,
                "",
                "cannot find symbol\nsymbol: class MissingType",
                Duration.ofMillis(50)
        );

        assertEquals(RepairFailureType.COMPILATION_MISSING_TYPE, classifier.classify(result));
    }

    @Test
    void classifiesMissingSymbol() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.COMPILATION_FAILED,
                null,
                null,
                "cannot find symbol\nsymbol: method doWork()",
                null,
                "",
                "cannot find symbol\nsymbol: method doWork()",
                Duration.ofMillis(50)
        );

        assertEquals(RepairFailureType.COMPILATION_MISSING_SYMBOL, classifier.classify(result));
    }

    @Test
    void classifiesOtherCompilationWhenNoSpecificSignal() {
        TestExecutionResult result = new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.COMPILATION_FAILED,
                null,
                null,
                "compilation failed due to unknown javac error",
                null,
                "",
                "",
                Duration.ofMillis(50)
        );

        assertEquals(RepairFailureType.COMPILATION_OTHER, classifier.classify(result));
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
