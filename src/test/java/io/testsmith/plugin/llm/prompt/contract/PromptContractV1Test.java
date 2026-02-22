package io.testsmith.plugin.llm.prompt.contract;

import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.api.LlmTuning;
import io.testsmith.plugin.llm.api.TestFramework;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptContractV1Test {

    @Test
    void serializesToStableJson() {
        PromptContractV1 contract = new PromptContractV1(
                PromptContractV1.VERSION,
                "com.example.Service",
                "package com.example;\npublic class Service {}",
                List.of("package com.example; class Dep {}"),
                TestFramework.JUNIT5,
                "Use AssertJ",
                GenerationMode.GENERATE,
                "",
                LlmTuning.defaults()
        );

        String expected = "{"
                + "\"contractVersion\":\"v1\","
                + "\"targetClassFqcn\":\"com.example.Service\","
                + "\"targetClassSource\":\"package com.example;\\npublic class Service {}\","
                + "\"relatedSources\":[\"package com.example; class Dep {}\"],"
                + "\"testFramework\":\"JUNIT5\","
                + "\"projectTestPatternNotes\":\"Use AssertJ\","
                + "\"generationMode\":\"GENERATE\","
                + "\"failureContext\":\"\","
                + "\"tuning\":{\"temperature\":0.1,\"topP\":0.9,\"repeatPenalty\":1.1}"
                + "}";

        assertEquals(expected, contract.toJson());
    }
}
