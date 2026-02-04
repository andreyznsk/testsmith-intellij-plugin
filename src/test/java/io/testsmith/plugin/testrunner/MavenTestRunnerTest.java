package io.testsmith.plugin.testrunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.testsmith.plugin.testrunner.model.TestExecutionStatus;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenTestRunnerTest {

    @Test
    void verifyTargetUsesClassNameAndExtraArgsOrder(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(new ExecResult(0, "", "", false));
        MavenTestRunner runner = new MavenTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("MyTest", null),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of("-DskipTests=false"),
                null
        );

        runner.run(request);

        assertEquals(
                List.of("mvn", "-q", "-DskipTests=false", "-Dtest=MyTest", "test"),
                executor.command()
        );
    }

    @Test
    void verifyTargetUsesMethodFilter(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(new ExecResult(0, "", "", false));
        MavenTestRunner runner = new MavenTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("MyTest", "testOne"),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        runner.run(request);

        assertEquals(
                List.of("mvn", "-q", "-Dtest=MyTest#testOne", "test"),
                executor.command()
        );
    }

    @Test
    void classifiesCompilationFailure(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(
                new ExecResult(1, "COMPILATION ERROR", "", false)
        );
        MavenTestRunner runner = new MavenTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("MyTest", null),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        var result = runner.run(request);

        assertFalse(result.isSuccess());
        assertEquals(TestExecutionStatus.COMPILATION_FAILED, result.status());
    }

    @Test
    void classifiesTestFailure(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(
                new ExecResult(1, "There are test failures", "", false)
        );
        MavenTestRunner runner = new MavenTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("MyTest", null),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        var result = runner.run(request);

        assertFalse(result.isSuccess());
        assertEquals(TestExecutionStatus.TEST_FAILED, result.status());
    }

    @Test
    void classifiesInfraFailure(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(
                new ExecResult(1, "Could not resolve dependencies", "", false)
        );
        MavenTestRunner runner = new MavenTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("MyTest", null),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        var result = runner.run(request);

        assertFalse(result.isSuccess());
        assertEquals(TestExecutionStatus.INFRASTRUCTURE_ERROR, result.status());
    }

    @Test
    void fullSuiteRequiresJacocoXml(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(new ExecResult(0, "", "", false));
        MavenTestRunner runner = new MavenTestRunner(true, executor);
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

        var result = runner.run(request);

        assertFalse(result.isSuccess());
        assertEquals(TestExecutionStatus.INFRASTRUCTURE_ERROR, result.status());
        assertTrue(result.failureMessage().contains(jacocoXml.toString()));
    }

    @Test
    void timeoutMapsToTimeout(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(new ExecResult(1, "", "", true));
        MavenTestRunner runner = new MavenTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("MyTest", null),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        var result = runner.run(request);

        assertEquals(TestExecutionStatus.TIMEOUT, result.status());
        assertTrue(result.failureMessage().contains("timed out"));
    }

    @Test
    void exitZeroMapsToSuccess(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(new ExecResult(0, "", "", false));
        MavenTestRunner runner = new MavenTestRunner(true, executor);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                new TestTarget("MyTest", null),
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        );

        var result = runner.run(request);

        assertEquals(TestExecutionStatus.SUCCESS, result.status());
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
