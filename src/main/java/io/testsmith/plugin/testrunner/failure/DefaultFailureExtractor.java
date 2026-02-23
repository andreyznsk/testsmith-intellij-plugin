package io.testsmith.plugin.testrunner.failure;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DefaultFailureExtractor implements FailureExtractor {
    private static final int MAX_EVIDENCE_LINES = 200;
    private static final int DEFAULT_TAIL_LINES = 60;
    private static final int COMPILATION_BLOCK_MAX = 50;
    private static final int TEST_FAILURE_BLOCK_MAX = 60;
    private static final int INFRA_BLOCK_MAX = 80;
    private static final int TIMEOUT_TAIL_LINES = 30;

    private static final Pattern GRADLE_TEST_FAILED =
            Pattern.compile("^\\s*(.+?)\\s*>\\s*(.+?)\\s+FAILED\\s*$");
    private static final Pattern JAVA_COMPILER_PATH_ERROR =
            Pattern.compile(".*:\\d+(?::\\d+)?\\s+error:.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_COMPILER_ERROR =
            Pattern.compile(".*\\berror:\\s+.+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_COMPILER_ERRORS =
            Pattern.compile(".*\\berrors?:\\s+.+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_COMPILER_ERROR_PAREN =
            Pattern.compile(".*\\berror\\(s\\):\\s+.+", Pattern.CASE_INSENSITIVE);
    private static final String[] INFRA_NEEDLES = {
            "could not resolve",
            "could not determine the dependencies",
            "no matching toolchains found",
            "cannot find a java installation",
            "gradle build daemon disappeared unexpectedly",
            "could not find or load main class",
            "permission denied",
            "no such file or directory",
            "could not resolve all files"
    };

    @Override
    public FailureReport extract(String stdout, String stderr, int exitCode, boolean timedOut) {
        String safeStdout = stdout == null ? "" : stdout;
        String safeStderr = stderr == null ? "" : stderr;
        if (timedOut) {
            List<String> evidence = LogSlicer.tail(safeStdout, safeStderr, TIMEOUT_TAIL_LINES);
            evidence = ensureEvidence(evidence, safeStdout, safeStderr);
            return new FailureReport(
                    FailureKind.TIMEOUT,
                    "Process timed out",
                    "Process timed out",
                    List.of(),
                    evidence,
                    Map.of()
            );
        }

        if (exitCode == 0) {
            return FailureReport.none();
        }

        List<String> lines = LogSlicer.combinedLines(safeStdout, safeStderr);

        FailureReport report = detectMavenCompilation(lines, safeStdout, safeStderr);
        if (report != null) {
            return report;
        }
        report = detectGradleCompilation(lines, safeStdout, safeStderr);
        if (report != null) {
            return report;
        }
        report = detectMavenTestFailure(lines, safeStdout, safeStderr);
        if (report != null) {
            return report;
        }
        report = detectGradleTestFailure(lines, safeStdout, safeStderr);
        if (report != null) {
            return report;
        }
        report = detectInfrastructure(lines, safeStdout, safeStderr);
        if (report != null) {
            return report;
        }

        List<String> evidence = LogSlicer.tail(lines, DEFAULT_TAIL_LINES);
        evidence = ensureEvidence(evidence, safeStdout, safeStderr);
        String rootCause = firstNonEmpty(evidence);
        if (rootCause.isEmpty()) {
            rootCause = "Build failed";
        }
        return new FailureReport(
                FailureKind.INFRASTRUCTURE,
                "Build failure",
                rootCause,
                List.of(),
                evidence,
                Map.of()
        );
    }

    private FailureReport detectMavenCompilation(
            List<String> lines,
            String stdout,
            String stderr
    ) {
        boolean trigger = containsAnyLines(
                lines,
                "compilation error",
                "compilation failure"
        ) || containsAllLines(lines, "failed to execute goal", "maven-compiler-plugin");
        if (!trigger) {
            return null;
        }

        int startIndex = indexOf(lines, line -> line.contains("COMPILATION ERROR"));
        if (startIndex < 0) {
            startIndex = indexOf(lines, line -> line.contains("Compilation failure"));
        }
        List<String> evidence = startIndex >= 0
                ? LogSlicer.slice(lines, startIndex, COMPILATION_BLOCK_MAX)
                : LogSlicer.tail(lines, DEFAULT_TAIL_LINES);
        evidence = ensureEvidence(evidence, stdout, stderr);

        String rootCause = firstCompilerError(lines);
        if (rootCause.isEmpty()) {
            rootCause = firstNonEmpty(evidence);
        }
        String summary = rootCause.isEmpty() ? "Compilation error" : "Compilation error: " + rootCause;
        return new FailureReport(
                FailureKind.COMPILATION,
                summary,
                rootCause,
                List.of(),
                evidence,
                Map.of()
        );
    }

    private FailureReport detectGradleCompilation(
            List<String> lines,
            String stdout,
            String stderr
    ) {
        boolean trigger = containsAnyLines(
                lines,
                "compilation failed",
                "compilejava failed",
                "compiletestjava failed",
                "compilekotlin failed",
                "compiletestkotlin failed",
                "execution failed for task ':compilejava'",
                "execution failed for task ':compiletestjava'"
        );
        if (!trigger) {
            return null;
        }
        if (containsAnyLines(lines, INFRA_NEEDLES)) {
            return null;
        }

        int startIndex = indexOf(lines, line -> line.contains("Compilation failed"));
        if (startIndex < 0) {
            startIndex = indexOf(lines, line -> line.contains("Execution failed for task ':compile"));
        }
        List<String> evidence = startIndex >= 0
                ? LogSlicer.slice(lines, startIndex, COMPILATION_BLOCK_MAX)
                : LogSlicer.tail(lines, DEFAULT_TAIL_LINES);
        evidence = ensureEvidence(evidence, stdout, stderr);

        String rootCause = firstCompilerError(lines);
        if (rootCause.isEmpty()) {
            rootCause = firstNonEmpty(evidence);
        }
        String summary = rootCause.isEmpty() ? "Compilation error" : "Compilation error: " + rootCause;
        return new FailureReport(
                FailureKind.COMPILATION,
                summary,
                rootCause,
                List.of(),
                evidence,
                Map.of()
        );
    }

    private FailureReport detectMavenTestFailure(
            List<String> lines,
            String stdout,
            String stderr
    ) {
        boolean trigger = containsAnyLines(
                lines,
                "there are test failures",
                "failed tests:",
                "<<< failure!",
                "<<< error!"
        ) || hasSurefireSummary(lines);
        if (!trigger) {
            return null;
        }

        List<String> failedTestsLines = collectFailedTestLines(lines);
        List<String> failingTests = parseFailedTests(failedTestsLines, lines);
        List<String> evidence = !failedTestsLines.isEmpty()
                ? LogSlicer.limit(failedTestsLines, TEST_FAILURE_BLOCK_MAX)
                : fallbackTestEvidence(lines);
        evidence = ensureEvidence(evidence, stdout, stderr);

        String rootCause = failingTests.isEmpty() ? firstNonEmpty(evidence) : String.join("\n", failingTests);
        String summary = failingTests.isEmpty() ? "Test failure" : "Test failed: " + failingTests.get(0);
        return new FailureReport(
                FailureKind.TEST_FAILURE,
                summary,
                rootCause,
                failingTests,
                evidence,
                Map.of()
        );
    }

    private FailureReport detectGradleTestFailure(
            List<String> lines,
            String stdout,
            String stderr
    ) {
        boolean trigger = containsAnyLines(
                lines,
                "there were failing tests",
                "task :test failed",
                "execution failed for task ':test'",
                "see the report at:"
        );
        if (!trigger) {
            return null;
        }

        Set<String> failingTests = new LinkedHashSet<>();
        List<String> evidenceLines = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = GRADLE_TEST_FAILED.matcher(line);
            if (matcher.matches()) {
                String className = matcher.group(1).trim();
                String methodName = matcher.group(2).trim();
                failingTests.add(className + "#" + methodName);
                evidenceLines.add(line);
            }
        }

        String reportPath = extractReportPath(lines);
        if (!reportPath.isEmpty()) {
            evidenceLines.add("See the report at: " + reportPath);
        }
        if (evidenceLines.isEmpty()) {
            evidenceLines = fallbackTestEvidence(lines);
        }
        List<String> evidence = ensureEvidence(LogSlicer.limit(evidenceLines, TEST_FAILURE_BLOCK_MAX), stdout, stderr);
        List<String> failingTestsList = List.copyOf(failingTests);
        String rootCause = failingTestsList.isEmpty() ? firstNonEmpty(evidence) : String.join("\n", failingTestsList);
        String summary = failingTestsList.isEmpty() ? "Test failure" : "Test failed: " + failingTestsList.get(0);
        Map<String, String> hints = reportPath.isEmpty() ? Map.of() : Map.of("reportPath", reportPath);
        return new FailureReport(
                FailureKind.TEST_FAILURE,
                summary,
                rootCause,
                failingTestsList,
                evidence,
                hints
        );
    }

    private FailureReport detectInfrastructure(
            List<String> lines,
            String stdout,
            String stderr
    ) {
        boolean trigger = containsAnyLines(
                lines, INFRA_NEEDLES
        );
        if (!trigger) {
            return null;
        }

        Section section = findGradleWhatWentWrong(lines);
        List<String> evidence;
        String rootCause;
        if (section != null) {
            evidence = LogSlicer.limit(section.lines(), INFRA_BLOCK_MAX);
            rootCause = selectGradleRootCause(section.lines());
        } else {
            evidence = extractMavenErrorBlock(lines);
            rootCause = firstErrorLine(evidence);
        }
        evidence = ensureEvidence(evidence, stdout, stderr);
        if (rootCause.isEmpty()) {
            rootCause = firstNonEmpty(evidence);
        }
        String summary = rootCause.isEmpty() ? "Infrastructure failure" : "Infrastructure error: " + rootCause;
        return new FailureReport(
                FailureKind.INFRASTRUCTURE,
                summary,
                rootCause,
                List.of(),
                evidence,
                Map.of()
        );
    }

    private boolean containsAnyLines(List<String> lines, String... needles) {
        String[] normalizedNeedles = normalizeNeedles(needles);
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (String needle : normalizedNeedles) {
                if (needle.isEmpty()) {
                    continue;
                }
                if (lower.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAllLines(List<String> lines, String... needles) {
        String[] normalizedNeedles = normalizeNeedles(needles);
        boolean[] matched = new boolean[normalizedNeedles.length];
        int remaining = normalizedNeedles.length;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (int i = 0; i < normalizedNeedles.length; i++) {
                if (normalizedNeedles[i].isEmpty()) {
                    continue;
                }
                if (!matched[i] && lower.contains(normalizedNeedles[i])) {
                    matched[i] = true;
                    remaining--;
                }
            }
            if (remaining == 0) {
                return true;
            }
        }
        return false;
    }

    private String[] normalizeNeedles(String... needles) {
        if (needles == null || needles.length == 0) {
            return new String[0];
        }
        String[] normalized = new String[needles.length];
        for (int i = 0; i < needles.length; i++) {
            String needle = needles[i];
            normalized[i] = needle == null ? "" : needle.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    private int indexOf(List<String> lines, java.util.function.Predicate<String> predicate) {
        for (int i = 0; i < lines.size(); i++) {
            if (predicate.test(lines.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private List<String> ensureEvidence(List<String> evidence, String stdout, String stderr) {
        List<String> limited = LogSlicer.limit(evidence, MAX_EVIDENCE_LINES);
        if (!limited.isEmpty()) {
            return limited;
        }
        return LogSlicer.limit(LogSlicer.tail(stdout, stderr, DEFAULT_TAIL_LINES), MAX_EVIDENCE_LINES);
    }

    private String firstCompilerError(List<String> lines) {
        for (String line : lines) {
            String trimmed = stripLogPrefix(line).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (JAVA_COMPILER_PATH_ERROR.matcher(trimmed).matches()
                    || JAVA_COMPILER_ERROR.matcher(trimmed).matches()
                    || JAVA_COMPILER_ERROR_PAREN.matcher(trimmed).matches()
                    || JAVA_COMPILER_ERRORS.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return "";
    }

    private String stripLogPrefix(String line) {
        if (line == null) {
            return "";
        }
        return line.replaceFirst("^\\[[A-Z]+\\]\\s*", "");
    }

    private boolean hasSurefireSummary(List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("Tests run:") && trimmed.contains("Failures:")) {
                return true;
            }
        }
        return false;
    }

    private List<String> collectFailedTestLines(List<String> lines) {
        List<String> failed = new ArrayList<>();
        int failedIndex = indexOf(lines, line -> line.contains("Failed tests:"));
        if (failedIndex >= 0) {
            for (int i = failedIndex + 1; i < lines.size() && failed.size() < 20; i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    break;
                }
                if (trimmed.contains("Tests run:") || trimmed.startsWith("[INFO]")) {
                    break;
                }
                failed.add(line);
            }
        }
        for (String line : lines) {
            if (line.contains("<<< FAILURE!") || line.contains("<<< ERROR!")) {
                failed.add(line);
            }
        }
        return failed;
    }

    private List<String> parseFailedTests(List<String> failedLines, List<String> allLines) {
        Set<String> tests = new LinkedHashSet<>();
        for (String line : failedLines) {
            String parsed = parseTestIdFromLine(line);
            if (!parsed.isEmpty()) {
                tests.add(parsed);
            }
        }
        if (tests.isEmpty()) {
            for (String line : allLines) {
                if (line.contains("<<< FAILURE!") || line.contains("<<< ERROR!")) {
                    String parsed = parseTestIdFromLine(line);
                    if (!parsed.isEmpty()) {
                        tests.add(parsed);
                    }
                }
            }
        }
        return List.copyOf(tests);
    }

    private String parseTestIdFromLine(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = stripLogPrefix(line).trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.contains("<<<")) {
            trimmed = trimmed.substring(0, trimmed.indexOf("<<<")).trim();
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            trimmed = trimmed.substring(0, colon).trim();
        }
        if (trimmed.contains("(") && trimmed.contains(")")) {
            int open = trimmed.indexOf('(');
            int close = trimmed.indexOf(')', open);
            if (close > open) {
                String method = trimmed.substring(0, open).trim();
                String className = trimmed.substring(open + 1, close).trim();
                if (!method.isEmpty() && !className.isEmpty()) {
                    return className + "#" + method;
                }
            }
        }
        int space = trimmed.indexOf(' ');
        if (space > 0) {
            trimmed = trimmed.substring(0, space).trim();
        }
        if (trimmed.contains("#")) {
            return trimmed;
        }
        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot > 0 && lastDot < trimmed.length() - 1) {
            String className = trimmed.substring(0, lastDot);
            String methodName = trimmed.substring(lastDot + 1);
            if (!className.isEmpty() && !methodName.isEmpty()) {
                return className + "#" + methodName;
            }
        }
        return "";
    }

    private List<String> fallbackTestEvidence(List<String> lines) {
        int startIndex = indexOf(lines, line -> line.contains("Failed tests:"));
        if (startIndex < 0) {
            startIndex = indexOf(lines, line -> line.contains("There are test failures"));
        }
        if (startIndex < 0) {
            startIndex = indexOf(lines, line -> line.contains("There were failing tests"));
        }
        if (startIndex >= 0) {
            return LogSlicer.slice(lines, startIndex, TEST_FAILURE_BLOCK_MAX);
        }
        return LogSlicer.tail(lines, DEFAULT_TAIL_LINES);
    }

    private String extractReportPath(List<String> lines) {
        for (String line : lines) {
            int index = line.toLowerCase(Locale.ROOT).indexOf("see the report at:");
            if (index >= 0) {
                String value = line.substring(index + "see the report at:".length()).trim();
                return value;
            }
        }
        return "";
    }

    private Section findGradleWhatWentWrong(List<String> lines) {
        int startIndex = indexOf(lines, line -> line.trim().equals("* What went wrong:"));
        if (startIndex < 0) {
            return null;
        }
        List<String> sectionLines = new ArrayList<>();
        sectionLines.add(lines.get(startIndex));
        for (int i = startIndex + 1; i < lines.size() && sectionLines.size() < INFRA_BLOCK_MAX; i++) {
            String line = lines.get(i);
            if (line.startsWith("* ") && !line.startsWith("* What went wrong:")) {
                break;
            }
            sectionLines.add(line);
        }
        return new Section(sectionLines);
    }

    private String selectGradleRootCause(List<String> sectionLines) {
        for (String line : sectionLines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(">")) {
                String value = trimmed.substring(1).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        for (String line : sectionLines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.equals("* What went wrong:")) {
                return trimmed;
            }
        }
        return "";
    }

    private List<String> extractMavenErrorBlock(List<String> lines) {
        int startIndex = indexOf(lines, line -> line.contains("[ERROR]"));
        if (startIndex < 0) {
            return LogSlicer.tail(lines, DEFAULT_TAIL_LINES);
        }
        return LogSlicer.slice(lines, startIndex, INFRA_BLOCK_MAX);
    }

    private String firstErrorLine(List<String> lines) {
        for (String line : lines) {
            if (line.contains("[ERROR]")) {
                return stripLogPrefix(line).trim();
            }
        }
        return "";
    }

    private String firstNonEmpty(List<String> lines) {
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
        }
        return "";
    }

    private record Section(List<String> lines) {
        private Section {
            Objects.requireNonNull(lines, "lines must not be null");
        }
    }
}
