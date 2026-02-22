package io.testsmith.plugin.agent.generation.structured;

import java.util.Objects;

public record ValidationResult(
        boolean valid,
        ValidationErrorType errorType,
        String message
) {
    public ValidationResult {
        if (valid) {
            errorType = null;
            message = "";
        } else {
            Objects.requireNonNull(errorType, "errorType must not be null when valid=false");
            message = message == null ? "" : message;
        }
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, null, "");
    }

    public static ValidationResult invalid(ValidationErrorType errorType, String message) {
        return new ValidationResult(false, errorType, message);
    }
}
