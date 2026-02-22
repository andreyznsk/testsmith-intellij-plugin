package io.testsmith.plugin.llm.ollama;

import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.LlmMisconfigurationException;
import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.prompt.PromptComposer;

import java.net.http.HttpClient;
import java.util.Objects;

public final class OllamaLlmClient implements LlmClient {
    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "qwen2.5-coder:7b";

    private final PromptComposer promptComposer;
    private final OllamaApi ollamaApi;

    public OllamaLlmClient() {
        this(DEFAULT_BASE_URL, DEFAULT_MODEL, HttpClient.newHttpClient());
    }

    public OllamaLlmClient(String baseUrl, String model, HttpClient httpClient) {
        this(new PromptComposer(), new HttpOllamaApi(baseUrl, model, httpClient));
    }

    OllamaLlmClient(PromptComposer promptComposer, OllamaApi ollamaApi) {
        this.promptComposer = Objects.requireNonNull(promptComposer, "promptComposer must not be null");
        this.ollamaApi = Objects.requireNonNull(ollamaApi, "ollamaApi must not be null");
    }

    @Override
    public String generateRaw(LlmRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String prompt = promptComposer.compose(request);
        return ollamaApi.generate(prompt, request);
    }

    static void validateConfig(String baseUrl, String model) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LlmMisconfigurationException("Ollama baseUrl must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new LlmMisconfigurationException("Ollama model must not be blank");
        }
    }
}
