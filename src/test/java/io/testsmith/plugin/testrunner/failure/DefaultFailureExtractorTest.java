package io.testsmith.plugin.testrunner.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultFailureExtractorTest {
    private final DefaultFailureExtractor extractor = new DefaultFailureExtractor();

    @Test
    void mavenCompilationError() {
        String log = load("maven_compilation_error.txt");
        FailureReport report = extractor.extract(log, "", 1, false);

        assertEquals(FailureKind.COMPILATION, report.kind());
        assertFalse(report.rootCause().isBlank());
        assertTrue(report.rootCause().contains("error:"));
        assertTrue(report.failingTests().isEmpty());
        assertEvidence(report.evidence());
    }

    @Test
    void mavenTestFailure() {
        String log = load("maven_test_failure.txt");
        FailureReport report = extractor.extract(log, "", 1, false);

        assertEquals(FailureKind.TEST_FAILURE, report.kind());
        assertFalse(report.rootCause().isBlank());
        assertEquals(
                List.of(
                        "com.example.MyTest#testOne",
                        "com.example.OtherTest#testTwo",
                        "com.example.ThirdTest#testThree"
                ),
                report.failingTests()
        );
        assertEvidence(report.evidence());
    }

    @Test
    void gradleCompilationError() {
        String log = load("gradle_compilation_error.txt");
        FailureReport report = extractor.extract(log, "", 1, false);

        assertEquals(FailureKind.COMPILATION, report.kind());
        assertFalse(report.rootCause().isBlank());
        assertTrue(report.rootCause().contains("error:"));
        assertEvidence(report.evidence());
    }

    @Test
    void gradleTestFailure() {
        String log = load("gradle_test_failure.txt");
        FailureReport report = extractor.extract(log, "", 1, false);

        assertEquals(FailureKind.TEST_FAILURE, report.kind());
        assertFalse(report.rootCause().isBlank());
        assertEquals(
                List.of(
                        "com.example.MyTest#testOne",
                        "com.example.OtherTest#testTwo"
                ),
                report.failingTests()
        );
        assertEquals("file:///work/build/reports/tests/test/index.html", report.hints().get("reportPath"));
        assertEvidence(report.evidence());
    }

    @Test
    void gradleInfraToolchainMissing() {
        String log = load("gradle_infra_toolchain_missing.txt");
        FailureReport report = extractor.extract(log, "", 1, false);

        assertEquals(FailureKind.INFRASTRUCTURE, report.kind());
        assertFalse(report.rootCause().isBlank());
        assertTrue(report.rootCause().contains("No matching toolchains found"));
        assertEvidence(report.evidence());
    }

    @Test
    void timeoutCase() {
        String log = load("timeout_case.txt");
        FailureReport report = extractor.extract(log, "", 1, true);

        assertEquals(FailureKind.TIMEOUT, report.kind());
        assertFalse(report.rootCause().isBlank());
        assertEvidence(report.evidence());
    }

    private void assertEvidence(List<String> evidence) {
        assertFalse(evidence.isEmpty());
        assertTrue(evidence.size() <= 200);
    }

    private String load(String name) {
        String path = "io/testsmith/plugin/testrunner/failure/" + name;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read resource: " + path, ex);
        }
    }
}
