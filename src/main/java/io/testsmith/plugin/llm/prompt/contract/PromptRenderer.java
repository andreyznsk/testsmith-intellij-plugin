package io.testsmith.plugin.llm.prompt.contract;

import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.prompt.response.LlmResponseContractV1;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Renders prompt contracts into stable, provider-neutral prompt strings.
 */
public final class PromptRenderer {

    public String render(PromptContractV1 contract) {
        Objects.requireNonNull(contract, "contract must not be null");
        StringJoiner joiner = new StringJoiner("\n");

        joiner.add("[TestSmith Prompt Contract " + contract.contractVersion() + "]");
        joiner.add("## Contract Version");
        joiner.add(contract.contractVersion());

        joiner.add("## Target Class FQCN");
        joiner.add(contract.targetClassFqcn());

        joiner.add("## Target Class Source");
        joiner.add(contract.targetClassSource());

        joiner.add("## Related Sources");
        appendRelatedSources(joiner, contract.relatedSources());

        joiner.add("## Detected Test Framework");
        joiner.add(contract.testFramework().name());

        joiner.add("## Project Test Pattern Notes");
        joiner.add(contract.projectTestPatternNotes().isBlank() ? "(none)" : contract.projectTestPatternNotes());

        joiner.add("## Generation Mode");
        joiner.add(contract.generationMode().name());

        joiner.add("## Failure Context");
        if (contract.generationMode() == GenerationMode.FIX && !contract.failureContext().isBlank()) {
            joiner.add(contract.failureContext());
        } else {
            joiner.add("(none)");
        }

        joiner.add("## Deterministic Generation Constraints");
        joiner.add("temperature=" + contract.tuning().temperature());
        joiner.add("topP=" + contract.tuning().topP());
        joiner.add("repeatPenalty=" + contract.tuning().repeatPenalty());

        joiner.add("## Constraints");
        joiner.add("- Do NOT invent classes, methods, constructors, or fields that are absent in provided sources.");
        joiner.add("- Use only APIs visible in the provided sources.");
        joiner.add("- Never modify build configuration, test infrastructure, or production code.");
        joiner.add("- javaSource must be a valid Java compilation unit string.");
        joiner.add("- javaSource must not contain markdown fences or wrapper text.");

        joiner.add("## Output Contract (MUST FOLLOW)");
        joiner.add("Return JSON only.");
        joiner.add("No markdown.");
        joiner.add("No explanations.");
        joiner.add("Output must be exactly one JSON object with this schema:");
        joiner.add("{");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_TEST_CLASS_FQCN + "\": string,");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_SUGGESTED_FILE_PATH + "\": string,");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_TEST_FRAMEWORK + "\": \"JUNIT4\" | \"JUNIT5\",");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_JAVA_SOURCE + "\": string,");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_NOTES + "\": string[]");
        joiner.add("}");

        return joiner.toString();
    }

    private static void appendRelatedSources(StringJoiner joiner, List<String> relatedSources) {
        if (relatedSources.isEmpty()) {
            joiner.add("(none)");
            return;
        }
        for (int i = 0; i < relatedSources.size(); i++) {
            joiner.add("[relatedSource:" + i + "]");
            joiner.add(relatedSources.get(i));
        }
    }
}
