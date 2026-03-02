package io.testsmith.plugin.llm;

import com.intellij.openapi.project.Project;
import io.testsmith.plugin.settings.LlmProvider;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import io.testsmith.plugin.settings.TestSmithSecretsStore;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

public final class LlmProviderConfigurationValidator {
    private final TestSmithSecretsStore secretsStore;

    public LlmProviderConfigurationValidator() {
        this(new TestSmithSecretsStore());
    }

    LlmProviderConfigurationValidator(@NotNull TestSmithSecretsStore secretsStore) {
        this.secretsStore = Objects.requireNonNull(secretsStore, "secretsStore");
    }

    public @NotNull Optional<String> validate(@NotNull Project project, TestSmithProjectSettings settings) {
        Objects.requireNonNull(project, "project");
        String openAiKey = secretsStore.getOpenAiKey(project).orElse("");
        String gigaChatKey = secretsStore.getGigaChatKey(project).orElse("");
        return validate(settings, openAiKey, gigaChatKey);
    }

    @NotNull Optional<String> validate(TestSmithProjectSettings settings, String openAiKey, String gigaChatKey) {
        LlmProvider provider = resolveProvider(settings);
        TestSmithProjectSettings effectiveSettings = settings == null ? new TestSmithProjectSettings() : settings;

        return switch (provider) {
            case OLLAMA -> {
                if (effectiveSettings.ollama == null
                        || isBlank(effectiveSettings.ollama.baseUrl)
                        || isBlank(effectiveSettings.ollama.model)) {
                    yield Optional.of("Ollama is not configured: base URL and model are required.");
                }
                yield Optional.empty();
            }
            case OPENAI -> Optional.of("Provider not supported yet: OpenAI.");
            case GIGACHAT -> Optional.of("Provider not supported yet: GigaChat.");
        };
    }

    public @NotNull LlmProvider resolveProvider(TestSmithProjectSettings settings) {
        if (settings == null || settings.provider == null) {
            return LlmProvider.OLLAMA;
        }
        return settings.provider;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
