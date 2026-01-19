package io.testsmith.plugin.testrunner;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    public TestRunResult run(TestRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant start = Instant.now();
        String stdout = "";
        String stderr = "";
        int exitCode = -1;
        Optional<TestFailureType> failureType = Optional.empty();
        Optional<String> failureSummary = Optional.empty();

        ExecResult execResult = processExecutor.exec(
                buildCommand(request),
                request.projectRoot(),
                request.env(),
                request.timeout()
        );
        stdout = execResult.stdout();
        stderr = execResult.stderr();
        exitCode = execResult.exitCode();
        if (execResult.timedOut()) {
            failureType = Optional.of(TestFailureType.INFRA_FAILURE);
            failureSummary = Optional.of("Process timed out after " + request.timeout().toSeconds() + "s");
        }

        if (failureType.isEmpty() && exitCode != 0) {
            String combined = stdout + "\n" + stderr;
            TestFailureType classified = classifyFailure(combined);
            failureType = Optional.of(classified);
            failureSummary = Optional.ofNullable(summaryFromOutput(combined));
        }

        if (failureType.isEmpty() && request.mode() == TestRunMode.FULL_SUITE_COVERAGE) {
            if (!Files.exists(request.jacocoXmlPath())) {
                failureType = Optional.of(TestFailureType.INFRA_FAILURE);
                failureSummary = Optional.of("JaCoCo XML not found at " + request.jacocoXmlPath());
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        boolean success = failureType.isEmpty();
        return new TestRunResult(
                success,
                exitCode,
                duration,
                stdout,
                stderr,
                failureType,
                failureSummary
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

    private TestFailureType classifyFailure(String output) {
        if (containsAny(output, "COMPILATION ERROR", "Compilation failure")) {
            return TestFailureType.COMPILATION_FAILURE;
        }
        if (output.contains("Failed to execute goal") && output.contains("maven-compiler-plugin")) {
            return TestFailureType.COMPILATION_FAILURE;
        }
        if (containsAny(output, "Tests run:", "There are test failures", "Failed tests:", "Error(s):")) {
            return TestFailureType.TEST_FAILURE;
        }
        return TestFailureType.INFRA_FAILURE;
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
}
