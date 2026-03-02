package io.testsmith.plugin.llm;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.LlmMisconfigurationException;
import io.testsmith.plugin.llm.gigachat.GigaChatLlmClient;
import io.testsmith.plugin.llm.ollama.OllamaLlmClient;
import io.testsmith.plugin.llm.openai.OpenAiLlmClient;
import io.testsmith.plugin.settings.LlmProvider;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import io.testsmith.plugin.settings.TestSmithSecretsStore;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpClient;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LlmClientFactory {
    private static final Logger LOG = Logger.getInstance(LlmClientFactory.class);

    private final LlmProviderConfigurationValidator validator;
    private final TestSmithSecretsStore secretsStore;
    private final Supplier<HttpClient> httpClientSupplier;
    private final Consumer<String> infoLogger;
    private final Consumer<String> warnLogger;

    public LlmClientFactory() {
        this(
                new LlmProviderConfigurationValidator(),
                new TestSmithSecretsStore(),
                HttpClient::newHttpClient,
                LOG::info,
                LOG::warn
        );
    }

    LlmClientFactory(
            @NotNull LlmProviderConfigurationValidator validator,
            @NotNull TestSmithSecretsStore secretsStore,
            @NotNull Supplier<HttpClient> httpClientSupplier,
            @NotNull Consumer<String> infoLogger,
            @NotNull Consumer<String> warnLogger
    ) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.secretsStore = Objects.requireNonNull(secretsStore, "secretsStore");
        this.httpClientSupplier = Objects.requireNonNull(httpClientSupplier, "httpClientSupplier");
        this.infoLogger = Objects.requireNonNull(infoLogger, "infoLogger");
        this.warnLogger = Objects.requireNonNull(warnLogger, "warnLogger");
    }

    public @NotNull LlmClient create(@NotNull Project project, TestSmithProjectSettings settings) {
        Objects.requireNonNull(project, "project");
        String openAiKey = secretsStore.getOpenAiKey(project).orElse("");
        String gigaChatKey = secretsStore.getGigaChatKey(project).orElse("");
        return create(settings, openAiKey, gigaChatKey);
    }

    @NotNull LlmClient create(TestSmithProjectSettings settings, String openAiKey, String gigaChatKey) {
        TestSmithProjectSettings effectiveSettings = settings == null ? new TestSmithProjectSettings() : settings;
        LlmProvider provider = validator.resolveProvider(effectiveSettings);
        Optional<String> validationError = validator.validate(effectiveSettings, openAiKey, gigaChatKey);
        if (validationError.isPresent()) {
            String message = validationError.get();
            warnLogger.accept("LLM provider " + provider + " misconfiguration: " + message);
            throw new LlmMisconfigurationException(message);
        }

        infoLogger.accept("LLM provider selected for run start: " + provider);

        return switch (provider) {
            case OLLAMA -> {
                TestSmithProjectSettings.OllamaConfig config = effectiveSettings.ollama == null
                        ? new TestSmithProjectSettings.OllamaConfig()
                        : effectiveSettings.ollama;
                yield new OllamaLlmClient(config.baseUrl, config.model, httpClientSupplier.get());
            }
            case OPENAI -> {
                TestSmithProjectSettings.OpenAiConfig config = effectiveSettings.openAi == null
                        ? new TestSmithProjectSettings.OpenAiConfig()
                        : effectiveSettings.openAi;
                yield new OpenAiLlmClient(openAiKey, config.model, config.temperature, config.maxTokens);
            }
            case GIGACHAT -> {
                TestSmithProjectSettings.GigaChatConfig config = effectiveSettings.gigaChat == null
                        ? new TestSmithProjectSettings.GigaChatConfig()
                        : effectiveSettings.gigaChat;
                yield new GigaChatLlmClient(gigaChatKey, config.model, config.endpoint);
            }
        };
    }
}
