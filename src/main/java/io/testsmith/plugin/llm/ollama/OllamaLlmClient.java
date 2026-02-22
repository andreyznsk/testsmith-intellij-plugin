package io.testsmith.plugin.llm.ollama;

import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.LlmMisconfigurationException;
import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.api.LlmResponse;
import io.testsmith.plugin.llm.prompt.PromptComposer;
import io.testsmith.plugin.llm.prompt.response.LlmResponseParser;
import java.net.http.HttpClient;
import java.util.Objects;

public final class OllamaLlmClient implements LlmClient {
    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "qwen2.5-coder:7b";

    private final PromptComposer promptComposer;
    private final LlmResponseParser responseParser;
    private final OllamaApi ollamaApi;

    public OllamaLlmClient() {
        this(DEFAULT_BASE_URL, DEFAULT_MODEL, HttpClient.newHttpClient());
    }

    public OllamaLlmClient(String baseUrl, String model, HttpClient httpClient) {
        this(new PromptComposer(), new LlmResponseParser(), new HttpOllamaApi(baseUrl, model, httpClient));
    }

    OllamaLlmClient(PromptComposer promptComposer, LlmResponseParser responseParser, OllamaApi ollamaApi) {
        this.promptComposer = Objects.requireNonNull(promptComposer, "promptComposer must not be null");
        this.responseParser = Objects.requireNonNull(responseParser, "responseParser must not be null");
        this.ollamaApi = Objects.requireNonNull(ollamaApi, "ollamaApi must not be null");
    }

    @Override
    public LlmResponse generateTest(LlmRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String prompt = promptComposer.compose(request);
        String rawContent = ollamaApi.generate(prompt, request);
        return responseParser.parse(rawContent);
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
