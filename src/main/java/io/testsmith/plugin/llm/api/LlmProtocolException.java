package io.testsmith.plugin.llm.api;

public final class LlmProtocolException extends LlmException {
    public LlmProtocolException(String message) {
        super(message);
    }

    public LlmProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
