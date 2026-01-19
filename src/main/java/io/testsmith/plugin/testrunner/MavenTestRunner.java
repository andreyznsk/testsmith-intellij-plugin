package io.testsmith.plugin.testrunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class MavenTestRunner implements TestRunner {
    private final boolean quiet;

    public MavenTestRunner() {
        this(true);
    }

    public MavenTestRunner(boolean quiet) {
        this.quiet = quiet;
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

        List<String> command = buildCommand(request);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(request.projectRoot().toFile());
        builder.environment().putAll(request.env());

        try {
            Process process = builder.start();
            StreamCollector outCollector = new StreamCollector(process.getInputStream());
            StreamCollector errCollector = new StreamCollector(process.getErrorStream());
            Thread outThread = new Thread(outCollector, "maven-stdout-reader");
            Thread errThread = new Thread(errCollector, "maven-stderr-reader");
            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                exitCode = -1;
                failureType = Optional.of(TestFailureType.INFRA_FAILURE);
                failureSummary = Optional.of("Process timed out after " + request.timeout().toSeconds() + "s");
            } else {
                exitCode = process.exitValue();
            }

            outThread.join();
            errThread.join();
            stdout = outCollector.output();
            stderr = errCollector.output();
        } catch (IOException e) {
            failureType = Optional.of(TestFailureType.INFRA_FAILURE);
            failureSummary = Optional.of("Failed to start Maven process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failureType = Optional.of(TestFailureType.INFRA_FAILURE);
            failureSummary = Optional.of("Maven process interrupted");
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
        if (request.mode() == TestRunMode.VERIFY_TARGET) {
            args.add("-Dtest=" + request.target().toMavenFilter());
        }
        args.add("test");
        args.addAll(request.mavenArgsExtra());
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
        List<String> lines = output.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .limit(3)
                .collect(Collectors.toList());
        if (lines.isEmpty()) {
            return null;
        }
        return String.join(System.lineSeparator(), lines);
    }

    private static final class StreamCollector implements Runnable {
        private final InputStream inputStream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private StreamCollector(InputStream inputStream) {
            this.inputStream = Objects.requireNonNull(inputStream, "inputStream must not be null");
        }

        @Override
        public void run() {
            try (InputStream stream = inputStream) {
                byte[] chunk = new byte[4096];
                int read;
                while ((read = stream.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
            } catch (IOException ignored) {
                // Best effort; failures are handled via process exit and output diagnostics.
            }
        }

        private String output() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
