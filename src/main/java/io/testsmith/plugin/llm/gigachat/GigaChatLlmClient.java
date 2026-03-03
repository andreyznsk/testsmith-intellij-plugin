package io.testsmith.plugin.llm.gigachat;

import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.LlmException;
import io.testsmith.plugin.llm.api.LlmRequest;

import java.util.Objects;

public final class GigaChatLlmClient implements LlmClient {
    private final String apiKey;
    private final String model;
    private final String endpoint;

    public GigaChatLlmClient(String apiKey, String model, String endpoint) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.model = Objects.requireNonNull(model, "model");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    }

    @Override
    public String generateRaw(LlmRequest request) {
        throw new LlmException("GigaChat client is not implemented yet.");
    }

    public String model() {
        return model;
    }

    public String endpoint() {
        return endpoint;
    }
}
