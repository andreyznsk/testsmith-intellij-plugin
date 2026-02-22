package io.testsmith.plugin.llm.prompt.contract;

import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.api.LlmTuning;
import io.testsmith.plugin.llm.api.TestFramework;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptRendererTest {
    private final PromptRenderer renderer = new PromptRenderer();

    @Test
    void rendersStablePrompt() {
        PromptContractV1 contract = new PromptContractV1(
                PromptContractV1.VERSION,
                "com.example.Service",
                "package com.example;\npublic class Service {}",
                List.of(
                        "package com.example; class DepA {}",
                        "package com.example; class DepB {}"
                ),
                TestFramework.JUNIT5,
                "Use AssertJ if available",
                GenerationMode.GENERATE,
                "",
                LlmTuning.defaults()
        );

        String expected = String.join("\n",
                "[TestSmith Prompt Contract v1]",
                "## Contract Version",
                "v1",
                "## Target Class FQCN",
                "com.example.Service",
                "## Target Class Source",
                "package com.example;",
                "public class Service {}",
                "## Related Sources",
                "[relatedSource:0]",
                "package com.example; class DepA {}",
                "[relatedSource:1]",
                "package com.example; class DepB {}",
                "## Detected Test Framework",
                "JUNIT5",
                "## Project Test Pattern Notes",
                "Use AssertJ if available",
                "## Generation Mode",
                "GENERATE",
                "## Failure Context",
                "(none)",
                "## Deterministic Generation Constraints",
                "temperature=0.1",
                "topP=0.9",
                "repeatPenalty=1.1",
                "## Constraints",
                "- Do NOT invent classes, methods, constructors, or fields that are absent in provided sources.",
                "- Use only APIs visible in the provided sources.",
                "- Never modify build configuration, test infrastructure, or production code.",
                "- javaSource must be a valid Java compilation unit string.",
                "- javaSource must not contain markdown fences or wrapper text.",
                "## Output Contract (MUST FOLLOW)",
                "Return JSON only.",
                "No markdown.",
                "No explanations.",
                "Output must be exactly one JSON object with this schema:",
                "{",
                "  \"testClassFqcn\": string,",
                "  \"suggestedFilePath\": string,",
                "  \"testFramework\": \"JUNIT4\" | \"JUNIT5\",",
                "  \"javaSource\": string,",
                "  \"notes\": string[]",
                "}"
        );

        assertEquals(expected, renderer.render(contract));
    }
}
