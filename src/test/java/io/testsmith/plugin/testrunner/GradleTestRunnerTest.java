package io.testsmith.plugin.testrunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleTestRunnerTest {

    @Test
    void verifyTargetUsesClassNameAndFilters(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(new ExecResult(0, "", "", false));
        GradleTestRunner runner = new GradleTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("com.example.MyTest", null),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        runner.run(request);

        assertEquals(
                List.of("gradle", "--quiet", "test", "--tests", "com.example.MyTest"),
                executor.command()
        );
    }

    @Test
    void verifyTargetUsesMethodFilter(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(new ExecResult(0, "", "", false));
        GradleTestRunner runner = new GradleTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("com.example.MyTest", "testOne"),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        runner.run(request);

        assertEquals(
                List.of("gradle", "--quiet", "test", "--tests", "com.example.MyTest.testOne"),
                executor.command()
        );
    }

    @Test
    void classifiesCompilationFailure(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(
                new ExecResult(1, "Compilation failed", "", false)
        );
        GradleTestRunner runner = new GradleTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("com.example.MyTest", null),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        TestRunResult result = runner.run(request);

        assertFalse(result.success());
        assertEquals(Optional.of(TestFailureType.COMPILATION_FAILURE), result.failureType());
    }

    @Test
    void classifiesTestFailure(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(
                new ExecResult(1, "There were failing tests", "", false)
        );
        GradleTestRunner runner = new GradleTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("com.example.MyTest", null),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        TestRunResult result = runner.run(request);

        assertFalse(result.success());
        assertEquals(Optional.of(TestFailureType.TEST_FAILURE), result.failureType());
    }

    @Test
    void classifiesInfraFailure(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(
                new ExecResult(1, "Could not resolve dependencies", "", false)
        );
        GradleTestRunner runner = new GradleTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("com.example.MyTest", null),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        TestRunResult result = runner.run(request);

        assertFalse(result.success());
        assertEquals(Optional.of(TestFailureType.INFRA_FAILURE), result.failureType());
    }

    @Test
    void fullSuiteRequiresJacocoXml(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(new ExecResult(0, "", "", false));
        GradleTestRunner runner = new GradleTestRunner(true, executor);
        Path jacocoXml = tempDir.resolve("jacoco.xml");
        TestRunRequest request = new TestRunRequest(
                TestRunMode.FULL_SUITE_COVERAGE,
                null,
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                jacocoXml
        );

        TestRunResult result = runner.run(request);

        assertFalse(result.success());
        assertEquals(Optional.of(TestFailureType.INFRA_FAILURE), result.failureType());
        assertTrue(result.failureSummary().orElse("").contains(jacocoXml.toString()));
    }

    private static final class FakeExecutor implements ProcessExecutor {
        private final ExecResult result;
        private List<String> command = List.of();

        private FakeExecutor(ExecResult result) {
            this.result = result;
        }

        @Override
        public ExecResult exec(List<String> command, Path workingDirectory, Map<String, String> env, Duration timeout) {
            this.command = List.copyOf(command);
            return result;
        }

        private List<String> command() {
            return command;
        }
    }
}
