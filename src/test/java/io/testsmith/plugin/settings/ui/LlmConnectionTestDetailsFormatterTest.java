package io.testsmith.plugin.settings.ui;

import io.testsmith.plugin.llm.api.HealthCheckResult;
import io.testsmith.plugin.settings.LlmProvider;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmConnectionTestDetailsFormatterTest {
    @Test
    void detailsAreSanitizedAndContainCoreFields() {
        TestSmithProjectSettings settings = new TestSmithProjectSettings();
        settings.provider = LlmProvider.OLLAMA;
        settings.ollama.baseUrl = "http://user:secret@localhost:11434/api?api_key=qwerty";
        settings.ollama.model = "qwen2.5-coder:7b";

        String rawBearer = "Bearer super-secret-token-123456789";
        HealthCheckResult result = HealthCheckResult.failed(
                "OLLAMA",
                "Connection failed. " + rawBearer + " sk-abcdefghijklmnopqrstuvwxyz1234",
                "NETWORK_ERROR",
                123L
        );

        String details = LlmConnectionTestDetailsFormatter.format(settings, result);

        assertTrue(details.contains("Provider: Ollama"));
        assertTrue(details.contains("Endpoint:"));
        assertTrue(details.contains("Model: qwen2.5-coder:7b"));
        assertTrue(details.contains("Technical code: NETWORK_ERROR"));
        assertTrue(details.contains("Latency: 123 ms"));
        assertFalse(details.contains("super-secret-token-123456789"));
        assertFalse(details.contains("sk-abcdefghijklmnopqrstuvwxyz1234"));
        assertFalse(details.contains("api_key=qwerty"));
        assertFalse(details.contains("user:secret@"));
    }
}
