package io.testsmith.plugin.agent;

import com.intellij.openapi.diagnostic.Logger;
import io.testsmith.plugin.coverage.CoverageReader;
import io.testsmith.plugin.coverage.CoverageSnapshot;
import io.testsmith.plugin.coverage.JaCoCoXmlPathResolver;
import io.testsmith.plugin.coverage.JacocoXmlCoverageReader;
import io.testsmith.plugin.coverage.LineCoverage;
import io.testsmith.plugin.coverage.PackageCoverage;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class CoverageAnalyzeService {
    private static final Logger LOG = Logger.getInstance(CoverageAnalyzeService.class);

    private final JaCoCoXmlPathResolver pathResolver;
    private final CoverageReader coverageReader;

    public CoverageAnalyzeService() {
        this(new JaCoCoXmlPathResolver(), new JacocoXmlCoverageReader());
    }

    CoverageAnalyzeService(@NotNull JaCoCoXmlPathResolver pathResolver, @NotNull CoverageReader coverageReader) {
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.coverageReader = Objects.requireNonNull(coverageReader, "coverageReader");
    }

    public @NotNull CoverageAnalysisResult analyze(
            @Nullable String projectBasePath,
            @NotNull TestSmithProjectSettings settings
    ) throws CoverageAnalyzeException {
        Objects.requireNonNull(settings, "settings");

        String configuredPath = settings.jacocoXmlPath == null ? "" : settings.jacocoXmlPath.trim();
        if (configuredPath.isEmpty()) {
            throw new CoverageAnalyzeException(
                    "JaCoCo XML path is not configured. Set it in Settings | TestSmith | JaCoCo XML path.");
        }

        Optional<Path> resolved = pathResolver.resolve(projectBasePath, settings);
        Path jacocoXmlPath = resolved.orElse(Path.of(configuredPath));
        LOG.info("JaCoCo XML path=" + jacocoXmlPath);

        if (!Files.exists(jacocoXmlPath)) {
            throw new CoverageAnalyzeException(
                    "JaCoCo XML report was not found at configured path: " + jacocoXmlPath);
        }

        final CoverageSnapshot snapshot;
        try {
            snapshot = coverageReader.read(jacocoXmlPath);
        } catch (Exception ex) {
            throw new CoverageAnalyzeException(
                    "Failed to parse JaCoCo XML report. Verify the file is a valid JaCoCo XML report.", ex);
        }

        long covered = 0L;
        long missed = 0L;
        for (PackageCoverage packageCoverage : snapshot.packages().values()) {
            for (io.testsmith.plugin.coverage.ClassCoverage classCoverage : packageCoverage.classes().values()) {
                LineCoverage lineCoverage = classCoverage.lineCoverage();
                covered += lineCoverage.covered();
                missed += lineCoverage.missed();
            }
        }
        long total = covered + missed;
        double coveragePercent = total == 0L ? 100.0 : (covered * 100.0) / total;
        return new CoverageAnalysisResult(jacocoXmlPath, coveragePercent, covered, missed);
    }

    public record CoverageAnalysisResult(
            @NotNull Path jacocoXmlPath,
            double coveragePercent,
            long coveredLines,
            long missedLines
    ) {
    }

    public static final class CoverageAnalyzeException extends Exception {
        public CoverageAnalyzeException(@NotNull String message) {
            super(message);
        }

        public CoverageAnalyzeException(@NotNull String message, @NotNull Throwable cause) {
            super(message, cause);
        }
    }
}
