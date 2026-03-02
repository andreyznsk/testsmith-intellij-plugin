package io.testsmith.plugin.llm;

import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.LlmMisconfigurationException;
import io.testsmith.plugin.llm.gigachat.GigaChatLlmClient;
import io.testsmith.plugin.llm.ollama.OllamaLlmClient;
import io.testsmith.plugin.llm.openai.OpenAiLlmClient;
import io.testsmith.plugin.settings.LlmProvider;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import io.testsmith.plugin.settings.TestSmithSecretsStore;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmClientFactoryTest {
    @Test
    void defaultProviderResolvesToOllamaClient() {
        LlmClientFactory factory = newFactory(new ArrayList<>(), new ArrayList<>());

        LlmClient client = factory.create(new TestSmithProjectSettings(), "", "");

        assertInstanceOf(OllamaLlmClient.class, client);
    }

    @Test
    void switchingProviderChangesClientImplementation() {
        LlmClientFactory factory = newFactory(new ArrayList<>(), new ArrayList<>());
        TestSmithProjectSettings settings = new TestSmithProjectSettings();

        settings.provider = LlmProvider.OPENAI;
        LlmClient openAiClient = factory.create(settings, "sk-openai", "");
        assertInstanceOf(OpenAiLlmClient.class, openAiClient);

        settings.provider = LlmProvider.GIGACHAT;
        settings.gigaChat.endpoint = "https://gigachat.example/api";
        LlmClient gigaChatClient = factory.create(settings, "", "gigachat-token");
        assertInstanceOf(GigaChatLlmClient.class, gigaChatClient);
    }

    @Test
    void missingProviderConfigReturnsActionableError() {
        LlmClientFactory factory = newFactory(new ArrayList<>(), new ArrayList<>());
        TestSmithProjectSettings settings = new TestSmithProjectSettings();
        settings.provider = LlmProvider.OPENAI;

        LlmMisconfigurationException error = assertThrows(
                LlmMisconfigurationException.class,
                () -> factory.create(settings, "", "")
        );

        assertTrue(error.getMessage().contains("OpenAI is not configured"));
    }

    @Test
    void misconfigurationLogDoesNotLeakSecrets() {
        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        LlmClientFactory factory = newFactory(infoLogs, warnLogs);
        TestSmithProjectSettings settings = new TestSmithProjectSettings();
        settings.provider = LlmProvider.OPENAI;
        settings.openAi.model = "";
        String secretToken = "sk-top-secret-token";

        assertThrows(LlmMisconfigurationException.class, () -> factory.create(settings, secretToken, ""));

        assertTrue(warnLogs.stream().noneMatch(message -> message.contains(secretToken)));
    }

    @Test
    void nullProviderFallsBackToOllama() {
        LlmProviderConfigurationValidator validator = new LlmProviderConfigurationValidator(new TestSmithSecretsStore());
        TestSmithProjectSettings settings = new TestSmithProjectSettings();
        settings.provider = null;

        assertTrue(validator.resolveProvider(settings) == LlmProvider.OLLAMA);
    }

    private static LlmClientFactory newFactory(List<String> infoLogs, List<String> warnLogs) {
        return new LlmClientFactory(
                new LlmProviderConfigurationValidator(new TestSmithSecretsStore()),
                new TestSmithSecretsStore(),
                HttpClient::newHttpClient,
                infoLogs::add,
                warnLogs::add
        );
    }
}
