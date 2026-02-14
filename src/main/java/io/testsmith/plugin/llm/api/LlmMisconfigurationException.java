package io.testsmith.plugin.llm.api;

public final class LlmMisconfigurationException extends LlmException {
    public LlmMisconfigurationException(String message) {
        super(message);
    }

    public LlmMisconfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
