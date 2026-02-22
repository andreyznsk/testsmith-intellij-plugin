package io.testsmith.plugin.llm.prompt.contract;

import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.api.LlmTuning;
import io.testsmith.plugin.llm.api.TestFramework;
import io.testsmith.plugin.llm.internal.JsonCodec;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Versioned, serializable prompt contract for deterministic LLM test generation.
 */
public record PromptContractV1(
        String contractVersion,
        String targetClassFqcn,
        String targetClassSource,
        List<String> relatedSources,
        TestFramework testFramework,
        String projectTestPatternNotes,
        GenerationMode generationMode,
        String failureContext,
        LlmTuning tuning
) {
    public static final String VERSION = "v1";

    public PromptContractV1 {
        contractVersion = requireNonBlank(contractVersion, "contractVersion");
        targetClassFqcn = requireNonBlank(targetClassFqcn, "targetClassFqcn");
        targetClassSource = requireNonBlank(targetClassSource, "targetClassSource");
        Objects.requireNonNull(testFramework, "testFramework must not be null");
        Objects.requireNonNull(generationMode, "generationMode must not be null");
        Objects.requireNonNull(tuning, "tuning must not be null");

        relatedSources = relatedSources == null ? List.of() : List.copyOf(relatedSources);
        relatedSources.forEach(source -> requireNonBlank(source, "relatedSources item"));

        projectTestPatternNotes = projectTestPatternNotes == null ? "" : projectTestPatternNotes;
        failureContext = failureContext == null ? "" : failureContext;
    }

    public String toJson() {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        joiner.add(jsonField("contractVersion", contractVersion));
        joiner.add(jsonField("targetClassFqcn", targetClassFqcn));
        joiner.add(jsonField("targetClassSource", targetClassSource));
        joiner.add(jsonFieldRaw("relatedSources", toJsonArray(relatedSources)));
        joiner.add(jsonField("testFramework", testFramework.name()));
        joiner.add(jsonField("projectTestPatternNotes", projectTestPatternNotes));
        joiner.add(jsonField("generationMode", generationMode.name()));
        joiner.add(jsonField("failureContext", failureContext));
        joiner.add(jsonFieldRaw("tuning", toJsonTuning(tuning)));
        return joiner.toString();
    }

    private static String jsonField(String name, String value) {
        return JsonCodec.toJsonString(name) + ":" + JsonCodec.toJsonString(value);
    }

    private static String jsonFieldRaw(String name, String rawJsonValue) {
        return JsonCodec.toJsonString(name) + ":" + rawJsonValue;
    }

    private static String toJsonArray(List<String> values) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (String value : values) {
            joiner.add(JsonCodec.toJsonString(value));
        }
        return joiner.toString();
    }

    private static String toJsonTuning(LlmTuning tuning) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        joiner.add(JsonCodec.toJsonString("temperature") + ":" + tuning.temperature());
        joiner.add(JsonCodec.toJsonString("topP") + ":" + tuning.topP());
        joiner.add(JsonCodec.toJsonString("repeatPenalty") + ":" + tuning.repeatPenalty());
        return joiner.toString();
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
