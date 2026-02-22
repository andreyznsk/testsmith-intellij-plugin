package io.testsmith.plugin.llm.prompt;

import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.prompt.contract.PromptContractFactory;
import io.testsmith.plugin.llm.prompt.contract.PromptContractV1;
import io.testsmith.plugin.llm.prompt.contract.PromptRenderer;

import java.util.Objects;

/**
 * Deterministic provider-neutral prompt composer for test generation/fix requests.
 */
public final class PromptComposer {
    private final PromptContractFactory contractFactory;
    private final PromptRenderer renderer;

    public PromptComposer() {
        this(new PromptContractFactory(), new PromptRenderer());
    }

    PromptComposer(PromptContractFactory contractFactory, PromptRenderer renderer) {
        this.contractFactory = Objects.requireNonNull(contractFactory, "contractFactory must not be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
    }

    public String compose(LlmRequest request) {
        PromptContractV1 contract = contractFactory.fromRequest(request);
        return renderer.render(contract);
    }
}
