package io.testsmith.plugin.testrunner;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    public TestRunResult run(TestRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateRequest(request);
        Instant start = Instant.now();
        String stdout = "";
        String stderr = "";
        int exitCode = -1;
        Optional<TestFailureType> failureType = Optional.empty();
        Optional<String> failureSummary = Optional.empty();
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
                failureSummary,
                command
        );
    }

    private List<String> buildCommand(TestRunRequest request) {
        List<String> args = new ArrayList<>();
        args.add(selectGradleBinary(request.projectRoot()));
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

    private TestFailureType classifyFailure(String output) {
        String normalized = output.toLowerCase();
        if (containsAny(normalized, "compilation error", "compilation failed", "compilejava failed", "compiletestjava failed",
                "compilekotlin failed", "compiletestkotlin failed")) {
            return TestFailureType.COMPILATION_FAILURE;
        }
        if (containsAny(normalized, "there were failing tests", "execution failed for task ':test'",
                "task :test failed", "tests failed", "test failed")) {
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
        return upper.contains("ERROR") || upper.contains("FAILURE") || upper.contains("COMPILATION")
                || upper.contains("CAUSED BY") || upper.contains("TASK");
    }

    private void validateRequest(TestRunRequest request) {
        if (request.mode() == TestRunMode.VERIFY_TARGET && request.target() == null) {
            throw new IllegalArgumentException("target must be provided for VERIFY_TARGET");
        }
        if (request.mode() == TestRunMode.FULL_SUITE_COVERAGE && request.jacocoXmlPath() == null) {
            throw new IllegalArgumentException("jacocoXmlPath must be provided for FULL_SUITE_COVERAGE");
        }
    }

    private String selectGradleBinary(java.nio.file.Path projectRoot) {
        java.nio.file.Path wrapper = projectRoot.resolve("gradlew");
        if (Files.exists(wrapper)) {
            return "./gradlew";
        }
        java.nio.file.Path wrapperBat = projectRoot.resolve("gradlew.bat");
        if (Files.exists(wrapperBat)) {
            return "gradlew.bat";
        }
        return "gradle";
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
