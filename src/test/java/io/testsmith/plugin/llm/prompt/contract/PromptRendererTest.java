package io.testsmith.plugin.llm.prompt.contract;

import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.api.LlmTuning;
import io.testsmith.plugin.llm.api.TestFramework;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "- Never request or create infrastructure in this iteration.",
                "- Output must represent a single test only (no batch output).",
                "- No markdown, no prose, JSON only.",
                "## Output Contract (MUST FOLLOW)",
                "Return JSON only.",
                "No markdown.",
                "No explanations.",
                "Output must be exactly one JSON object with this schema:",
                "{",
                "  \"version\": \"1.0\",",
                "  \"action\": \"GENERATE_TEST\",",
                "  \"targetClass\": string,",
                "  \"testClassName\": string,",
                "  \"imports\": string[],",
                "  \"code\": string,",
                "  \"assumptions\": string[],",
                "  \"requiresInfrastructure\": boolean,",
                "  \"confidence\": number (optional),",
                "  \"metadata\": object (optional)",
                "}",
                "## Failure Example (INVALID)",
                "INVALID: {\"code\":\"Here is your test...\"}",
                "## Valid Example",
                "{",
                "  \"version\": \"1.0\",",
                "  \"action\": \"GENERATE_TEST\",",
                "  \"targetClass\": \"com.example.Service\",",
                "  \"testClassName\": \"ServiceTest\",",
                "  \"imports\": [\"org.junit.jupiter.api.Test\"],",
                "  \"code\": \"package com.example;\\npublic class ServiceTest {}\",",
                "  \"assumptions\": [],",
                "  \"requiresInfrastructure\": false",
                "}"
        );

        assertEquals(expected, renderer.render(contract));
    }

    @Test
    void rendersRepairContractForFixMode() {
        PromptContractV1 contract = new PromptContractV1(
                PromptContractV1.VERSION,
                "com.example.Service",
                "package com.example;\npublic class Service {}",
                List.of(),
                TestFramework.JUNIT5,
                "",
                GenerationMode.FIX,
                "Compilation failed: cannot find symbol",
                LlmTuning.defaults()
        );

        String rendered = renderer.render(contract);
        assertTrue(rendered.contains("\"version\": \"1.1\""));
        assertTrue(rendered.contains("\"action\": \"REPAIR_TEST\""));
    }
}
