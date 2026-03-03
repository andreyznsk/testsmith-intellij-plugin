package io.testsmith.plugin.agent;

import com.intellij.openapi.project.Project;
import io.testsmith.plugin.settings.BuildToolMode;
import io.testsmith.plugin.settings.LlmProvider;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import io.testsmith.plugin.settings.TestSmithProjectSettingsService;
import io.testsmith.plugin.settings.TestSmithSecretsStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class AgentPreflightValidator {
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
        TestSmithSecretsStore secretsStore = new TestSmithSecretsStore();
        LlmProvider provider = settings.provider == null ? LlmProvider.OLLAMA : settings.provider;
        return switch (provider) {
            case OLLAMA -> {
                if (settings.ollama == null || isBlank(settings.ollama.baseUrl) || isBlank(settings.ollama.model)) {
                    yield "Ollama is not configured: base URL and model are required.";
                }
                yield null;
            }
            case OPENAI -> {
                String key = secretsStore.getOpenAiKey(project).orElse("");
                if (key.isBlank() || settings.openAi == null || isBlank(settings.openAi.model)) {
                    yield "OpenAI is not configured: API key and model are required.";
                }
                yield null;
            }
            case GIGACHAT -> {
                String key = secretsStore.getGigaChatKey(project).orElse("");
                if (key.isBlank() || settings.gigaChat == null || isBlank(settings.gigaChat.model) || isBlank(settings.gigaChat.endpoint)) {
                    yield "GigaChat is not configured: API key, model and endpoint are required.";
                }
                yield null;
            }
        };
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

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
