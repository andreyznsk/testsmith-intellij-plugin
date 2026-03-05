package io.testsmith.plugin.llm.api;

/**
 * Provider-agnostic transport contract for test generation/fix requests.
 */
public interface LlmClient {
    /**
     * Sends a generation/fix request and returns raw LLM text output.
     *
     * <p>Implementations should map transport/protocol failures to {@link LlmException} subtypes.
     * Structured schema parsing/validation is enforced in the agent layer.
     */
    String generateRaw(LlmRequest request) throws LlmException;

    /**
     * Performs a lightweight provider connectivity/auth check.
     * Implementations must not trigger text generation and should use strict timeouts.
     */
    default HealthCheckResult healthCheck() {
        return HealthCheckResult.failed(
                "UNKNOWN",
                "Health check is not implemented for this provider.",
                "NOT_IMPLEMENTED",
                null
        );
    }
}
