package io.testsmith.plugin.llm.api;

import java.util.List;
import java.util.Objects;

public record LlmResponse(
        String testClassFqcn,
        String suggestedFilePath,
        TestFramework testFramework,
        String javaSource,
        List<String> notes
) {
    public LlmResponse {
        testClassFqcn = requireNonBlank(testClassFqcn, "testClassFqcn");
        suggestedFilePath = suggestedFilePath == null ? "" : suggestedFilePath;
        Objects.requireNonNull(testFramework, "testFramework must not be null");
        javaSource = requireNonBlank(javaSource, "javaSource");
        notes = notes == null ? List.of() : List.copyOf(notes);
        notes.forEach(note -> Objects.requireNonNull(note, "notes item must not be null"));
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
