package io.testsmith.plugin.agent;

import com.intellij.openapi.project.Project;
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
    private static final LlmProviderConfigurationValidator LLM_CONFIGURATION_VALIDATOR =
            new LlmProviderConfigurationValidator();

    private AgentPreflightValidator() {
    }

    public static @NotNull Optional<String> validate(@NotNull Project project) {
        if (project.isDisposed()) {
            return Optional.of("Project is disposed.");
        }

        TestSmithProjectSettings settings = TestSmithProjectSettingsService.getInstance(project).getSettings();
        if (settings.jacocoXmlPath == null || settings.jacocoXmlPath.isBlank()) {
            return Optional.of("JaCoCo XML path must be configured before Run.");
        }
        if (!isBuildToolDetected(project, settings.buildToolMode)) {
            return Optional.of("Build tool is not detected. Configure Build tool in settings or ensure pom.xml / build.gradle exists.");
        }

        String llmError = validateLlmConfiguration(project, settings);
        return llmError == null ? Optional.empty() : Optional.of(llmError);
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
