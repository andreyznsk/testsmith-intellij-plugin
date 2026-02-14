package io.testsmith.plugin.llm.prompt;

import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.api.LlmTuning;
import io.testsmith.plugin.llm.api.TestFramework;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptComposerTest {
    private final PromptComposer composer = new PromptComposer();

    @Test
    void includesSchemaAndKeyConstraints() {
        LlmRequest request = new LlmRequest(
                "com.example.Service",
                "package com.example; public class Service {}",
                List.of("package com.example; class Dep {}"),
                TestFramework.JUNIT5,
                "Use AssertJ if available",
                GenerationMode.GENERATE,
                "",
                LlmTuning.defaults(),
                Duration.ofSeconds(10),
                Map.of("traceId", "abc")
        );

        String prompt = composer.compose(request);

        assertTrue(prompt.contains("Return JSON ONLY"));
        assertTrue(prompt.contains("\"testClassFqcn\": string"));
        assertTrue(prompt.contains("Do NOT invent classes, methods, constructors, or fields"));
        assertTrue(prompt.contains("Required test framework: JUNIT5"));
    }
}
