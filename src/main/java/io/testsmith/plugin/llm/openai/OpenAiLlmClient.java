package io.testsmith.plugin.llm.openai;

import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.LlmException;
import io.testsmith.plugin.llm.api.LlmRequest;

import java.util.Objects;

public final class OpenAiLlmClient implements LlmClient {
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public OpenAiLlmClient(String apiKey, String model, double temperature, int maxTokens) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.model = Objects.requireNonNull(model, "model");
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Override
    public String generateRaw(LlmRequest request) {
        throw new LlmException("OpenAI client is not implemented yet.");
    }

    public String model() {
        return model;
    }

    public double temperature() {
        return temperature;
    }

    public int maxTokens() {
        return maxTokens;
    }
}
