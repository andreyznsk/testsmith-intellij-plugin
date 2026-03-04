package io.testsmith.plugin.llm.ollama;

import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.api.HealthCheckResult;

interface OllamaApi {
    String generate(String prompt, LlmRequest request);

    HealthCheckResult healthCheck();
}
