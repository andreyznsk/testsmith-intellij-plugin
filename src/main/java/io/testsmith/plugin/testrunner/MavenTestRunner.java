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

public final class MavenTestRunner implements TestRunner {
    private final boolean quiet;
    private final ProcessExecutor processExecutor;

    public MavenTestRunner() {
        this(true, new DefaultProcessExecutor());
    }

    public MavenTestRunner(boolean quiet) {
        this(quiet, new DefaultProcessExecutor());
    }

    public MavenTestRunner(boolean quiet, ProcessExecutor processExecutor) {
        this.quiet = quiet;
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor must not be null");
    }

    @Override
    public TestExecutionResult run(TestRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");
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
        args.add("mvn");
        if (quiet) {
            args.add("-q");
        }
        args.addAll(request.mavenArgsExtra());
        if (request.mode() == TestRunMode.VERIFY_TARGET) {
            args.add("-Dtest=" + request.target().toMavenFilter());
        }
        args.add("test");
        return List.copyOf(args);
    }

    private TestExecutionStatus classifyFailure(String output) {
        String normalized = output.toLowerCase();
        if (containsAny(normalized, "compilation error", "compilation failure")) {
            return TestExecutionStatus.COMPILATION_FAILED;
        }
        if (normalized.contains("failed to execute goal") && normalized.contains("maven-compiler-plugin")) {
            return TestExecutionStatus.COMPILATION_FAILED;
        }
        if (containsAny(normalized, "tests run:", "there are test failures", "failed tests:", "error(s):")) {
            return TestExecutionStatus.TEST_FAILED;
        }
        if (containsAny(normalized,
                "could not resolve dependencies",
                "could not resolve",
                "no such file or directory",
                "unknownhostexception",
                "permission denied",
                "not found",
                "failed to read artifact descriptor",
                "could not transfer artifact")) {
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
        return upper.contains("ERROR") || upper.contains("FAILURE") || upper.contains("COMPILATION");
    }

    private TestExecutionPhase toPhase(TestRunMode mode) {
        return mode == TestRunMode.VERIFY_TARGET
                ? TestExecutionPhase.VERIFY_TARGET
                : TestExecutionPhase.FULL_SUITE_COVERAGE;
    }
}
