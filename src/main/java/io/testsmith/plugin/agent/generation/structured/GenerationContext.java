package io.testsmith.plugin.agent.generation.structured;

import java.util.List;
import java.util.Objects;

public record GenerationContext(
        StructuredAction requestedAction,
        String requestedTargetClass,
        List<String> discoveredAnnotations
) {
    public GenerationContext {
        Objects.requireNonNull(requestedAction, "requestedAction must not be null");
        requestedTargetClass = requireNonBlank(requestedTargetClass, "requestedTargetClass");
        discoveredAnnotations = discoveredAnnotations == null ? List.of() : List.copyOf(discoveredAnnotations);
        discoveredAnnotations.forEach(annotation -> requireNonBlank(annotation, "discoveredAnnotations item"));
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
