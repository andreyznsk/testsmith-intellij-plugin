package io.testsmith.plugin.llm.api;

public final class LlmRefusalException extends LlmException {
    public LlmRefusalException(String message) {
        super(message);
    }

    public LlmRefusalException(String message, Throwable cause) {
        super(message, cause);
    }
}
