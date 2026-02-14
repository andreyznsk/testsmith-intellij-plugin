package io.testsmith.plugin.llm.prompt;

import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.api.LlmRequest;
import java.util.StringJoiner;

/**
 * Deterministic provider-neutral prompt composer for test generation/fix requests.
 */
public final class PromptComposer {

    public String compose(LlmRequest request) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("You are TestSmith's Java test generation engine.");
        joiner.add("Return JSON ONLY. Do not include markdown, code fences, or explanatory prose.");
        joiner.add("Output must be exactly one JSON object with this schema:");
        joiner.add("{");
        joiner.add("  \"testClassFqcn\": string,");
        joiner.add("  \"suggestedFilePath\": string,");
        joiner.add("  \"testFramework\": \"JUNIT4\" | \"JUNIT5\",");
        joiner.add("  \"javaSource\": string,");
        joiner.add("  \"notes\": string[]");
        joiner.add("}");
        joiner.add("Constraints:");
        joiner.add("- javaSource must be a valid Java compilation unit string.");
        joiner.add("- javaSource must not contain markdown fences or wrapper text.");
        joiner.add("- Do NOT invent classes, methods, constructors, or fields that are absent in provided sources.");
        joiner.add("- Use only APIs visible in provided context.");
        joiner.add("- Required test framework: " + request.testFramework().name());
        joiner.add("- Generation mode: " + request.mode().name());
        if (request.mode() == GenerationMode.FIX) {
            joiner.add("- Failure context:\n" + request.failureContext());
        }

        joiner.add("Target class fqcn: " + request.targetClassFqcn());
        joiner.add("Target class source:");
        joiner.add(request.targetClassSource());

        joiner.add("Project test pattern notes:");
        joiner.add(request.projectTestPatternNotes().isBlank() ? "(none)" : request.projectTestPatternNotes());

        joiner.add("Related sources:");
        if (request.relatedSources().isEmpty()) {
            joiner.add("(none)");
        } else {
            for (int i = 0; i < request.relatedSources().size(); i++) {
                joiner.add("[relatedSource:" + i + "]");
                joiner.add(request.relatedSources().get(i));
            }
        }

        joiner.add("Remember: return JSON only.");
        return joiner.toString();
    }
}
