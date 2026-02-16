package io.testsmith.plugin.llm.prompt.contract;

import io.testsmith.plugin.llm.api.LlmRequest;
import java.util.Objects;

/**
 * Builds a versioned prompt contract from an {@link LlmRequest}.
 */
public final class PromptContractFactory {

    public PromptContractV1 fromRequest(LlmRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new PromptContractV1(
                PromptContractV1.VERSION,
                request.targetClassFqcn(),
                request.targetClassSource(),
                request.relatedSources(),
                request.testFramework(),
                request.projectTestPatternNotes(),
                request.mode(),
                request.failureContext(),
                request.tuning()
        );
    }
}
