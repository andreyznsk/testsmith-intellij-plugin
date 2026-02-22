package io.testsmith.plugin.agent.generation.structured;

import java.util.Objects;

public final class StructuredRetryPolicy {
    public static final int DEFAULT_MAX_RETRIES = 2;

    private final int maxRetries;

    public StructuredRetryPolicy() {
        this(DEFAULT_MAX_RETRIES);
    }

    public StructuredRetryPolicy(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        this.maxRetries = maxRetries;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public boolean shouldRetry(ValidationErrorType errorType, int retriesUsed) {
        Objects.requireNonNull(errorType, "errorType must not be null");
        if (retriesUsed >= maxRetries) {
            return false;
        }
        return errorType == ValidationErrorType.STRUCTURE_INVALID
                || errorType == ValidationErrorType.SCHEMA_INVALID
                || errorType == ValidationErrorType.SEMANTIC_INVALID;
    }

    public String retryHint(ValidationErrorType errorType, String message) {
        Objects.requireNonNull(errorType, "errorType must not be null");
        String reason = message == null || message.isBlank() ? "Validation failed" : message;
        return switch (errorType) {
            case STRUCTURE_INVALID -> "Previous response was not strict JSON. Return JSON only, no prose/markdown. " + reason;
            case SCHEMA_INVALID -> "Previous response violated required schema. Match schema exactly. " + reason;
            case SEMANTIC_INVALID -> "Previous response violated semantic constraints. Fix violations exactly. " + reason;
            case INFRASTRUCTURE_REQUIRED -> "Infrastructure-required responses are not allowed in this iteration.";
        };
    }
}
