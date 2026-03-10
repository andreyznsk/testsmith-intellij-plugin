package io.testsmith.plugin.agent;

import io.testsmith.plugin.settings.TestSmithProjectSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageAnalyzeServiceTest {
    private final CoverageAnalyzeService service = new CoverageAnalyzeService();

    @Test
    void analyzeFailsWhenPathIsNotConfigured() {
        TestSmithProjectSettings settings = new TestSmithProjectSettings();
        settings.jacocoXmlPath = "";

        CoverageAnalyzeService.CoverageAnalyzeException ex = assertThrows(
                CoverageAnalyzeService.CoverageAnalyzeException.class,
                () -> service.analyze(null, settings)
        );
        assertTrue(ex.getMessage().contains("not configured"));
    }

    @Test
    void analyzeFailsWhenConfiguredFileDoesNotExist(@TempDir Path tempDir) {
        TestSmithProjectSettings settings = new TestSmithProjectSettings();
        settings.jacocoXmlPath = tempDir.resolve("missing.xml").toString();

        CoverageAnalyzeService.CoverageAnalyzeException ex = assertThrows(
                CoverageAnalyzeService.CoverageAnalyzeException.class,
                () -> service.analyze(tempDir.toString(), settings)
        );
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void analyzeFailsWhenXmlIsMalformed(@TempDir Path tempDir) throws IOException {
        Path broken = tempDir.resolve("broken.xml");
        Files.writeString(broken, "<report><package></report>");
        TestSmithProjectSettings settings = new TestSmithProjectSettings();
        settings.jacocoXmlPath = broken.toString();

        CoverageAnalyzeService.CoverageAnalyzeException ex = assertThrows(
                CoverageAnalyzeService.CoverageAnalyzeException.class,
                () -> service.analyze(tempDir.toString(), settings)
        );
        assertTrue(ex.getMessage().contains("Failed to parse"));
    }

    @Test
    void analyzeComputesTotalCoverageFromJacocoXml() throws Exception {
        TestSmithProjectSettings settings = new TestSmithProjectSettings();
        settings.jacocoXmlPath = resourcePath("jacocoTest/happy-path-2pkgs-5classes.xml").toString();

        CoverageAnalyzeService.CoverageAnalysisResult result = service.analyze(null, settings);

        assertEquals(22L, result.coveredLines());
        assertEquals(158L, result.missedLines());
        assertEquals(12.22, result.coveragePercent(), 0.01);
    }

    private static Path resourcePath(String resource) throws URISyntaxException {
        var url = CoverageAnalyzeServiceTest.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Resource not found: " + resource);
        }
        return Path.of(url.toURI());
    }
}
