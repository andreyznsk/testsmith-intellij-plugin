package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.StructuredTest;
import io.testsmith.plugin.llm.api.TestFramework;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RepairContext(
        String targetClassFqcn,
        String targetClassSource,
        List<String> relatedSources,
        TestFramework testFramework,
        String projectTestPatternNotes,
        List<String> discoveredAnnotations,
        Duration llmTimeout,
        Map<String, String> metadata,
        RepairMode repairMode,
        int attemptNumber,
        StructuredTest lastGeneratedTest
) {
    public RepairContext {
        targetClassFqcn = requireNonBlank(targetClassFqcn, "targetClassFqcn");
        targetClassSource = requireNonBlank(targetClassSource, "targetClassSource");
        Objects.requireNonNull(testFramework, "testFramework must not be null");
        llmTimeout = requirePositive(llmTimeout, "llmTimeout");
        Objects.requireNonNull(repairMode, "repairMode must not be null");
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attemptNumber must be > 0");
        }

        relatedSources = relatedSources == null ? List.of() : List.copyOf(relatedSources);
        relatedSources.forEach(source -> requireNonBlank(source, "relatedSources item"));

        projectTestPatternNotes = projectTestPatternNotes == null ? "" : projectTestPatternNotes;

        discoveredAnnotations = discoveredAnnotations == null ? List.of() : List.copyOf(discoveredAnnotations);
        discoveredAnnotations.forEach(annotation -> requireNonBlank(annotation, "discoveredAnnotations item"));

        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        metadata.forEach((key, value) -> {
            requireNonBlank(key, "metadata key");
            requireNonBlank(value, "metadata value");
        });

        Objects.requireNonNull(lastGeneratedTest, "lastGeneratedTest must not be null");
    }

    public RepairContext withAttemptNumber(int nextAttempt, StructuredTest currentTest) {
        return new RepairContext(
                targetClassFqcn,
                targetClassSource,
                relatedSources,
                testFramework,
                projectTestPatternNotes,
                discoveredAnnotations,
                llmTimeout,
                metadata,
                repairMode,
                nextAttempt,
                currentTest
        );
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
