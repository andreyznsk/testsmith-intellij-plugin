package io.testsmith.plugin.llm.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stable request model for provider-agnostic LLM test generation/fix calls.
 */
public record LlmRequest(
        String targetClassFqcn,
        String targetClassSource,
        List<String> relatedSources,
        TestFramework testFramework,
        String projectTestPatternNotes,
        GenerationMode mode,
        String failureContext,
        LlmTuning tuning,
        Duration timeout,
        Map<String, String> metadata
) {
    public LlmRequest {
        targetClassFqcn = requireNonBlank(targetClassFqcn, "targetClassFqcn");
        targetClassSource = requireNonBlank(targetClassSource, "targetClassSource");
        Objects.requireNonNull(testFramework, "testFramework must not be null");
        timeout = requirePositive(timeout, "timeout");

        relatedSources = relatedSources == null ? List.of() : List.copyOf(relatedSources);
        relatedSources.forEach(source -> requireNonBlank(source, "relatedSources item"));

        projectTestPatternNotes = projectTestPatternNotes == null ? "" : projectTestPatternNotes;
        mode = mode == null ? GenerationMode.GENERATE : mode;
        failureContext = failureContext == null ? "" : failureContext;
        tuning = tuning == null ? LlmTuning.defaults() : tuning;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);

        if (mode == GenerationMode.FIX && failureContext.isBlank()) {
            throw new IllegalArgumentException("failureContext must not be blank for FIX mode");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
