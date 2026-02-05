package io.testsmith.plugin.testrunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.testsmith.plugin.testrunner.model.TestExecutionStatus;
import io.testsmith.plugin.testrunner.failure.FailureKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
    void fullSuiteUsesTestTaskOnly(@TempDir Path tempDir) throws Exception {
        FakeExecutor executor = new FakeExecutor(new ExecResult(0, "", "", false));
        GradleTestRunner runner = new GradleTestRunner(true, executor);
        Path jacocoXml = tempDir.resolve("jacoco.xml");
        Files.createFile(jacocoXml);
        TestRunRequest request = new TestRunRequest(
                TestRunMode.FULL_SUITE_COVERAGE,
                null,
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                jacocoXml
        );

        runner.run(request);

        assertEquals(
                List.of("gradle", "test"),
                executor.command()
        );
    }

    @Test
    void usesCmdLauncherWhenGradlewBatPresent(@TempDir Path tempDir) throws Exception {
        // simulate Windows Gradle wrapper
        Path gradlewBat = tempDir.resolve("gradlew.bat");
        Files.writeString(gradlewBat, "@echo off");

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

        List<String> command = executor.command();

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            assertTrue(command.size() >= 3, "command must contain launcher + arguments");
            assertEquals("cmd", command.get(0));
            assertEquals("/c", command.get(1));
            assertEquals("gradlew.bat", command.get(2));
        } else {
            assertEquals("gradle", command.get(0));
        }
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

        var result = runner.run(request);

        assertFalse(result.isSuccess());
        assertEquals(TestExecutionStatus.COMPILATION_FAILED, result.status());
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

        var result = runner.run(request);

        assertFalse(result.isSuccess());
        assertEquals(TestExecutionStatus.TEST_FAILED, result.status());
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

        var result = runner.run(request);

        assertFalse(result.isSuccess());
        assertEquals(TestExecutionStatus.INFRASTRUCTURE_ERROR, result.status());
    }

    @Test
    void fullSuiteRequiresJacocoXml(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(new ExecResult(0, "out", "", false));
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

        var result = runner.run(request);

        assertFalse(result.isSuccess());
        assertEquals(TestExecutionStatus.INFRASTRUCTURE_ERROR, result.status());
        assertTrue(result.failureMessage().contains(jacocoXml.toString()));
        assertEquals(FailureKind.INFRASTRUCTURE, result.failureReport().kind());
        assertTrue(result.failureReport().summary().contains(jacocoXml.toString()));
        assertTrue(result.failureReport().rootCause().contains(jacocoXml.toString()));
        assertFalse(result.failureReport().evidence().isEmpty());
    }

    @Test
    void timeoutMapsToTimeout(@TempDir Path tempDir) {
        FakeExecutor executor = new FakeExecutor(new ExecResult(1, "", "", true));
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

        var result = runner.run(request);

        assertEquals(TestExecutionStatus.TIMEOUT, result.status());
        assertTrue(result.failureMessage().contains("timed out"));
    }

    @Test
    void exitZeroMapsToSuccess(@TempDir Path tempDir) {
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

        var result = runner.run(request);

        assertEquals(TestExecutionStatus.SUCCESS, result.status());
    }

    @Test
    void verifyTargetRequiresTarget(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> new TestRunRequest(
                TestRunMode.VERIFY_TARGET,
                null,
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        ));
    }

    @Test
    void fullSuiteRequiresJacocoPath(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> new TestRunRequest(
                TestRunMode.FULL_SUITE_COVERAGE,
                null,
                tempDir,
                Duration.ofSeconds(5),
                Map.of(),
                List.of(),
                null
        ));
    }

    @Test
    void prefersGradleWrapperWhenPresent(@TempDir Path tempDir) throws Exception {
        FakeExecutor executor = new FakeExecutor(new ExecResult(0, "", "", false));
        Files.createFile(tempDir.resolve("gradlew"));
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
                List.of("./gradlew", "--quiet", "test", "--tests", "com.example.MyTest"),
                executor.command()
        );
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
