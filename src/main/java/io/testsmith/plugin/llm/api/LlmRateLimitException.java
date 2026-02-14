package io.testsmith.plugin.llm.api;

public final class LlmRateLimitException extends LlmException {
    public LlmRateLimitException(String message) {
        super(message);
    }

    public LlmRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
