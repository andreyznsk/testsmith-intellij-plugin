package io.testsmith.plugin.agent.generation.structured;

import java.util.Objects;

public final class StructuredGenerationException extends RuntimeException {
    private final ValidationErrorType errorType;

    public StructuredGenerationException(ValidationErrorType errorType, String message) {
        super(message);
        this.errorType = Objects.requireNonNull(errorType, "errorType must not be null");
    }

    public StructuredGenerationException(ValidationErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType must not be null");
    }

    public ValidationErrorType errorType() {
        return errorType;
    }
}
