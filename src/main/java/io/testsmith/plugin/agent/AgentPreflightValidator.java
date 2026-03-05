package io.testsmith.plugin.agent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import io.testsmith.plugin.coverage.JaCoCoXmlPathResolver;
import io.testsmith.plugin.llm.LlmProviderConfigurationValidator;
import io.testsmith.plugin.settings.BuildToolMode;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import io.testsmith.plugin.settings.TestSmithProjectSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class AgentPreflightValidator {
    private static final Logger LOG = Logger.getInstance(AgentPreflightValidator.class);
    private static final LlmProviderConfigurationValidator LLM_CONFIGURATION_VALIDATOR =
            new LlmProviderConfigurationValidator();
    private static final JaCoCoXmlPathResolver JACOCO_XML_PATH_RESOLVER = new JaCoCoXmlPathResolver();

    private AgentPreflightValidator() {
    }

    public static @NotNull Optional<String> validate(@NotNull Project project) {
        LOG.debug("Run preflight started");
        if (project.isDisposed()) {
            LOG.debug("Run preflight failed: project is disposed");
            return Optional.of("Project is disposed.");
        }

        TestSmithProjectSettings settings = TestSmithProjectSettingsService.getInstance(project).getSettings();
        Optional<Path> jacocoXmlPath = JACOCO_XML_PATH_RESOLVER.resolve(project.getBasePath(), settings);
        if (jacocoXmlPath.isEmpty()) {
            LOG.debug("Run preflight failed: JaCoCo XML path could not be resolved");
            return Optional.of("JaCoCo XML report was not found. Configure JaCoCo XML path in settings or generate coverage report via Maven/Gradle.");
        }
        if (!isBuildToolDetected(project, settings.buildToolMode)) {
            LOG.debug("Run preflight failed: build tool is not detected");
            return Optional.of("Build tool is not detected. Configure Build tool in settings or ensure pom.xml / build.gradle exists.");
        }

        String llmError = validateLlmConfiguration(project, settings);
        if (llmError != null) {
            LOG.debug("Run preflight failed: LLM configuration error: " + llmError);
            return Optional.of(llmError);
        }

        LOG.debug("Run preflight passed. JaCoCo XML path: " + jacocoXmlPath.get());
        return Optional.empty();
    }

    private static @Nullable String validateLlmConfiguration(Project project, TestSmithProjectSettings settings) {
        return LLM_CONFIGURATION_VALIDATOR.validate(project, settings).orElse(null);
    }

    private static boolean isBuildToolDetected(Project project, BuildToolMode mode) {
        if (mode == BuildToolMode.MAVEN || mode == BuildToolMode.GRADLE) {
            return true;
        }
        String base = project.getBasePath();
        if (base == null || base.isBlank()) {
            return false;
        }
        Path basePath = Path.of(base);
        return Files.exists(basePath.resolve("pom.xml"))
                || Files.exists(basePath.resolve("build.gradle"))
                || Files.exists(basePath.resolve("build.gradle.kts"));
    }

}
