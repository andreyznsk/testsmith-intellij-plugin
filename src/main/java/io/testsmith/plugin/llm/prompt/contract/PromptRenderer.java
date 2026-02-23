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
        joiner.add("- Never request or create infrastructure in this iteration.");
        joiner.add("- Output must represent a single test only (no batch output).");
        joiner.add("- No markdown, no prose, JSON only.");

        joiner.add("## Output Contract (MUST FOLLOW)");
        joiner.add("Return JSON only.");
        joiner.add("No markdown.");
        joiner.add("No explanations.");
        joiner.add("Output must be exactly one JSON object with this schema:");
        boolean repairMode = contract.generationMode() == GenerationMode.FIX;
        String expectedVersion = repairMode
                ? LlmResponseContractV1.VERSION_REPAIR
                : LlmResponseContractV1.VERSION;
        String expectedAction = repairMode
                ? LlmResponseContractV1.ACTION_REPAIR_TEST
                : LlmResponseContractV1.ACTION_GENERATE_TEST;

        joiner.add("{");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_VERSION + "\": \"" + expectedVersion + "\",");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_ACTION + "\": \"" + expectedAction + "\",");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_TARGET_CLASS + "\": string,");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_TEST_CLASS_NAME + "\": string,");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_IMPORTS + "\": string[],");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_CODE + "\": string,");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_ASSUMPTIONS + "\": string[],");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_REQUIRES_INFRASTRUCTURE + "\": boolean,");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_CONFIDENCE + "\": number (optional),");
        joiner.add("  \"" + LlmResponseContractV1.FIELD_METADATA + "\": object (optional)");
        joiner.add("}");

        joiner.add("## Failure Example (INVALID)");
        joiner.add("INVALID: {\"code\":\"Here is your test...\"}");

        joiner.add("## Valid Example");
        joiner.add("{");
        joiner.add("  \"version\": \"" + expectedVersion + "\",");
        joiner.add("  \"action\": \"" + expectedAction + "\",");
        joiner.add("  \"targetClass\": \"com.example.Service\",");
        joiner.add("  \"testClassName\": \"ServiceTest\",");
        joiner.add("  \"imports\": [\"org.junit.jupiter.api.Test\"],");
        joiner.add("  \"code\": \"package com.example;\\npublic class ServiceTest {}\",");
        joiner.add("  \"assumptions\": [],");
        joiner.add("  \"requiresInfrastructure\": false");
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
