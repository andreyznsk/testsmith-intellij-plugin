package io.testsmith.plugin.llm;

import com.intellij.openapi.diagnostic.Logger;
import io.testsmith.plugin.llm.api.HealthCheckResult;
import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.gigachat.GigaChatLlmClient;
import io.testsmith.plugin.llm.ollama.OllamaLlmClient;
import io.testsmith.plugin.llm.openai.OpenAiLlmClient;
import io.testsmith.plugin.llm.security.SecretSanitizer;
import io.testsmith.plugin.settings.LlmProvider;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class LlmProviderHealthCheckService {
    private static final Logger LOG = Logger.getInstance(LlmProviderHealthCheckService.class);

    public @NotNull HealthCheckResult healthCheck(
            TestSmithProjectSettings settings,
            String openAiKey,
            String gigaChatKey
    ) {
        TestSmithProjectSettings effectiveSettings = settings == null ? new TestSmithProjectSettings() : settings;
        LlmProvider provider = effectiveSettings.provider == null ? LlmProvider.OLLAMA : effectiveSettings.provider;
        LOG.debug("LLM health check started. provider=" + provider);

        HealthCheckResult misconfiguration = validate(provider, effectiveSettings, openAiKey, gigaChatKey);
        if (misconfiguration != null) {
            LOG.debug("LLM health check blocked by misconfiguration. provider="
                    + provider
                    + " message="
                    + SecretSanitizer.sanitize(misconfiguration.userMessage()));
            return misconfiguration;
        }

        try {
            LlmClient client = createClient(provider, effectiveSettings, openAiKey, gigaChatKey);
            HealthCheckResult result = client.healthCheck();
            HealthCheckResult sanitizedResult = new HealthCheckResult(
                    result.status(),
                    SecretSanitizer.sanitize(result.userMessage()),
                    SecretSanitizer.sanitize(result.technicalCode()),
                    result.providerId(),
                    result.latencyMs()
            );
            LOG.debug("LLM health check finished. provider="
                    + provider
                    + " status="
                    + sanitizedResult.status()
                    + " technicalCode="
                    + sanitizedResult.technicalCode()
                    + " latencyMs="
                    + sanitizedResult.latencyMs());
            return sanitizedResult;
        } catch (RuntimeException ex) {
            String safeMessage = SecretSanitizer.sanitize(ex.getMessage());
            LOG.warn("LLM health check failed: provider=" + provider + " message=" + safeMessage);
            return HealthCheckResult.failed(provider.name(), "Health check failed.", ex.getClass().getSimpleName(), null);
        }
    }

    private LlmClient createClient(
            LlmProvider provider,
            TestSmithProjectSettings settings,
            String openAiKey,
            String gigaChatKey
    ) {
        return switch (provider) {
            case OLLAMA -> new OllamaLlmClient(settings.ollama.baseUrl, settings.ollama.model, java.net.http.HttpClient.newHttpClient());
            case OPENAI -> new OpenAiLlmClient(openAiKey, settings.openAi.model, settings.openAi.temperature, settings.openAi.maxTokens);
            case GIGACHAT -> new GigaChatLlmClient(gigaChatKey, settings.gigaChat.model, settings.gigaChat.endpoint);
        };
    }

    private HealthCheckResult validate(
            LlmProvider provider,
            TestSmithProjectSettings settings,
            String openAiKey,
            String gigaChatKey
    ) {
        Objects.requireNonNull(provider, "provider");
        switch (provider) {
            case OLLAMA -> {
                if (settings.ollama == null || isBlank(settings.ollama.baseUrl) || isBlank(settings.ollama.model)) {
                    return HealthCheckResult.failed("OLLAMA", "Ollama base URL and model are required.", "MISCONFIGURED", null);
                }
            }
            case OPENAI -> {
                if (isBlank(openAiKey)) {
                    return HealthCheckResult.failed("OPENAI", "OpenAI API key is required.", "MISCONFIGURED", null);
                }
                if (settings.openAi == null || isBlank(settings.openAi.model)) {
                    return HealthCheckResult.failed("OPENAI", "OpenAI model is required.", "MISCONFIGURED", null);
                }
            }
            case GIGACHAT -> {
                if (isBlank(gigaChatKey)) {
                    return HealthCheckResult.failed("GIGACHAT", "GigaChat API key is required.", "MISCONFIGURED", null);
                }
                if (settings.gigaChat == null || isBlank(settings.gigaChat.model) || isBlank(settings.gigaChat.endpoint)) {
                    return HealthCheckResult.failed("GIGACHAT", "GigaChat model and endpoint are required.", "MISCONFIGURED", null);
                }
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
