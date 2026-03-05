package io.testsmith.plugin.settings.ui;

import io.testsmith.plugin.llm.api.HealthCheckResult;
import io.testsmith.plugin.llm.security.SecretSanitizer;
import io.testsmith.plugin.settings.LlmProvider;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LlmConnectionTestDetailsFormatter {
    private LlmConnectionTestDetailsFormatter() {
    }

    public static @NotNull String format(@NotNull TestSmithProjectSettings settings, @NotNull HealthCheckResult result) {
        LlmProvider provider = settings.provider == null ? LlmProvider.OLLAMA : settings.provider;
        String providerName = readableProvider(result.providerId(), provider);
        String endpoint = resolveEndpoint(settings, provider);
        String model = resolveModel(settings, provider);
        String technicalCode = sanitizeOrDash(result.technicalCode());
        String message = resolveMessage(result);
        String latency = result.latencyMs() == null ? "-" : result.latencyMs() + " ms";

        return "Provider: " + providerName + "\n"
                + "Endpoint: " + endpoint + "\n"
                + "Model: " + model + "\n"
                + "Status: " + result.status() + "\n"
                + "Technical code: " + technicalCode + "\n"
                + "Message: " + message + "\n"
                + "Latency: " + latency;
    }

    private static @NotNull String resolveEndpoint(TestSmithProjectSettings settings, LlmProvider provider) {
        return switch (provider) {
            case OLLAMA -> sanitizeOrDash(settings.ollama == null ? null : settings.ollama.baseUrl);
            case OPENAI -> "https://api.openai.com";
            case GIGACHAT -> sanitizeOrDash(settings.gigaChat == null ? null : settings.gigaChat.endpoint);
        };
    }

    private static @NotNull String resolveModel(TestSmithProjectSettings settings, LlmProvider provider) {
        return switch (provider) {
            case OLLAMA -> sanitizeOrDash(settings.ollama == null ? null : settings.ollama.model);
            case OPENAI -> sanitizeOrDash(settings.openAi == null ? null : settings.openAi.model);
            case GIGACHAT -> sanitizeOrDash(settings.gigaChat == null ? null : settings.gigaChat.model);
        };
    }

    private static @NotNull String resolveMessage(@NotNull HealthCheckResult result) {
        String message = SecretSanitizer.sanitize(result.userMessage());
        if (!message.isBlank()) {
            return message;
        }
        return fallbackMessage(result.technicalCode());
    }

    private static @NotNull String fallbackMessage(@Nullable String technicalCode) {
        if (technicalCode == null || technicalCode.isBlank()) {
            return "Connection test failed.";
        }
        String code = technicalCode.toUpperCase();
        return switch (code) {
            case "TIMEOUT" -> "Timeout.";
            case "NETWORK_ERROR" -> "Cannot connect (connection refused).";
            case "AUTH_ERROR", "401", "403" -> "Authentication failed.";
            case "INVALID_BASE_URL" -> "Invalid base URL.";
            case "MISCONFIGURED" -> "Connection settings are incomplete.";
            default -> "Connection test failed.";
        };
    }

    private static @NotNull String readableProvider(@Nullable String providerId, LlmProvider fallback) {
        String id = providerId == null || providerId.isBlank() ? fallback.name() : providerId;
        return switch (id.toUpperCase()) {
            case "OPENAI" -> "OpenAI";
            case "GIGACHAT" -> "GigaChat";
            case "OLLAMA" -> "Ollama";
            default -> id;
        };
    }

    private static @NotNull String sanitizeOrDash(@Nullable String value) {
        String sanitized = SecretSanitizer.sanitize(value);
        return sanitized.isBlank() ? "-" : sanitized;
    }
}
