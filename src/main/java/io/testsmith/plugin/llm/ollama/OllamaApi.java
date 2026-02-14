package io.testsmith.plugin.llm.ollama;

import io.testsmith.plugin.llm.api.LlmRequest;

interface OllamaApi {
    String generate(String prompt, LlmRequest request);
}
