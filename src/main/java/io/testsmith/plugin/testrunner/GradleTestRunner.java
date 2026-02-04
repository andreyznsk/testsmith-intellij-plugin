package io.testsmith.plugin.testrunner;

import io.testsmith.plugin.testrunner.model.TestExecutionPhase;
import io.testsmith.plugin.testrunner.model.TestExecutionResult;
import io.testsmith.plugin.testrunner.model.TestExecutionStatus;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class GradleTestRunner implements TestRunner {
    private final boolean quiet;
    private final ProcessExecutor processExecutor;

    public GradleTestRunner() {
        this(true, new DefaultProcessExecutor());
    }

    public GradleTestRunner(boolean quiet) {
        this(quiet, new DefaultProcessExecutor());
    }

    public GradleTestRunner(boolean quiet, ProcessExecutor processExecutor) {
        this.quiet = quiet;
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor must not be null");
    }

    @Override
    public TestExecutionResult run(TestRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateRequest(request);
        Instant start = Instant.now();
        String stdout = "";
        String stderr = "";
        int exitCode = -1;
        List<String> command = buildCommand(request);

        ExecResult execResult = processExecutor.exec(
                command,
                request.projectRoot(),
                request.env(),
                request.timeout()
        );
        stdout = execResult.stdout();
        stderr = execResult.stderr();
        exitCode = execResult.exitCode();

        Duration duration = Duration.between(start, Instant.now());
        TestExecutionPhase phase = toPhase(request.mode());
        if (execResult.timedOut()) {
            return new TestExecutionResult(
                    phase,
                    TestExecutionStatus.TIMEOUT,
                    null,
                    null,
                    "Process timed out after " + request.timeout().toSeconds() + "s",
                    stdout,
                    stderr,
                    duration
            );
        }
        if (exitCode == 0) {
            if (request.mode() == TestRunMode.FULL_SUITE_COVERAGE && !Files.exists(request.jacocoXmlPath())) {
                return new TestExecutionResult(
                        phase,
                        TestExecutionStatus.INFRASTRUCTURE_ERROR,
                        null,
                        null,
                        "JaCoCo XML not found at " + request.jacocoXmlPath(),
                        stdout,
                        stderr,
                        duration
                );
            }
            return new TestExecutionResult(
                    phase,
                    TestExecutionStatus.SUCCESS,
                    null,
                    null,
                    null,
                    stdout,
                    stderr,
                    duration
            );
        }

        String combined = stdout + "\n" + stderr;
        TestExecutionStatus status = classifyFailure(combined);
        return new TestExecutionResult(
                phase,
                status,
                null,
                null,
                summaryFromOutput(combined),
                stdout,
                stderr,
                duration
        );
    }

    private List<String> buildCommand(TestRunRequest request) {
        List<String> args = new ArrayList<>();
        args.addAll(selectGradleLauncher(request.projectRoot()));
        if (quiet && request.mode() == TestRunMode.VERIFY_TARGET) {
            args.add("--quiet");
        }
        args.add("test");
        if (request.mode() == TestRunMode.VERIFY_TARGET) {
            ensureGradleTargetSafe(request.target());
            args.add("--tests");
            args.add(request.target().toGradleFilter());
        }
        return List.copyOf(args);
    }

    private TestExecutionStatus classifyFailure(String output) {
        String normalized = output.toLowerCase();
        if (containsAny(normalized,
                "compilation error",
                "compilation failed",
                "compilejava failed",
                "compiletestjava failed",
                "compilekotlin failed",
                "compiletestkotlin failed")) {
            return TestExecutionStatus.COMPILATION_FAILED;
        }
        if (containsAny(normalized,
                "there were failing tests",
                "execution failed for task ':test'",
                "task :test failed",
                "tests failed",
                "test failed")) {
            return TestExecutionStatus.TEST_FAILED;
        }
        if (containsAny(normalized,
                "could not resolve dependencies",
                "could not resolve all files",
                "could not resolve",
                "no such file or directory",
                "permission denied",
                "unknownhostexception",
                "not found")) {
            return TestExecutionStatus.INFRASTRUCTURE_ERROR;
        }
        return TestExecutionStatus.TEST_FAILED;
    }

    private boolean containsAny(String output, String... needles) {
        for (String needle : needles) {
            if (output.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String summaryFromOutput(String output) {
        List<String> important = output.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(this::isFailureSignal)
                .limit(3)
                .collect(Collectors.toList());
        if (!important.isEmpty()) {
            return String.join(System.lineSeparator(), important);
        }
        List<String> fallback = output.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .limit(3)
                .collect(Collectors.toList());
        if (fallback.isEmpty()) {
            return null;
        }
        return String.join(System.lineSeparator(), fallback);
    }

    private boolean isFailureSignal(String line) {
        String upper = line.toUpperCase();
        return upper.contains("ERROR") || upper.contains("FAILURE") || upper.contains("COMPILATION")
                || upper.contains("CAUSED BY") || upper.contains("TASK");
    }

    private TestExecutionPhase toPhase(TestRunMode mode) {
        return mode == TestRunMode.VERIFY_TARGET
                ? TestExecutionPhase.VERIFY_TARGET
                : TestExecutionPhase.FULL_SUITE_COVERAGE;
    }

    private void validateRequest(TestRunRequest request) {
        if (request.mode() == TestRunMode.VERIFY_TARGET && request.target() == null) {
            throw new IllegalArgumentException("target must be provided for VERIFY_TARGET");
        }
        if (request.mode() == TestRunMode.FULL_SUITE_COVERAGE && request.jacocoXmlPath() == null) {
            throw new IllegalArgumentException("jacocoXmlPath must be provided for FULL_SUITE_COVERAGE");
        }
    }

    private List<String> selectGradleLauncher(java.nio.file.Path projectRoot) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        java.nio.file.Path wrapper = projectRoot.resolve("gradlew");
        java.nio.file.Path wrapperBat = projectRoot.resolve("gradlew.bat");

        if (isWindows && Files.exists(wrapperBat)) {
            return List.of("cmd", "/c", "gradlew.bat");
        }
        if (!isWindows && Files.exists(wrapper)) {
            return List.of("./gradlew");
        }
        return List.of("gradle");
    }

    private void ensureGradleTargetSafe(TestTarget target) {
        if (containsShellChars(target.className()) || containsShellChars(target.methodName())) {
            throw new IllegalArgumentException("Gradle test target contains unsupported whitespace or quotes");
        }
    }

    private boolean containsShellChars(String value) {
        if (value == null) {
            return false;
        }
        for (char ch : value.toCharArray()) {
            if (Character.isWhitespace(ch) || ch == '"' || ch == '\'') {
                return true;
            }
        }
        return false;
    }
}
