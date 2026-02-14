package io.testsmith.plugin.llm.api;

/**
 * Provider-agnostic client contract for test generation/fix requests.
 */
public interface LlmClient {
    /**
     * Generates or fixes a test according to the {@link LlmRequest} contract.
     *
     * <p>Implementations must return a response parsed from strict JSON output and should map
     * transport/protocol failures to {@link LlmException} subtypes.
     */
    LlmResponse generateTest(LlmRequest request) throws LlmException;
}
