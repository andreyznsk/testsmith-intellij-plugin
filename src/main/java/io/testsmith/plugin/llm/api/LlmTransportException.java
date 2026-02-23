package io.testsmith.plugin.llm.api;

public final class LlmTransportException extends LlmException {
    public LlmTransportException(String message) {
        super(message);
    }

    public LlmTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
