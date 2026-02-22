package io.testsmith.plugin.agent.generation.structured;

import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.LlmRequest;
import java.util.Objects;

public final class StructuredGenerationGateway {
    private static final String RETRY_NOTES_PREFIX = "[structured-retry] ";

    private final LlmClient llmClient;
    private final StructuredResponseParser parser;
    private final StructuredValidator validator;
    private final StructuredRetryPolicy retryPolicy;

    public StructuredGenerationGateway(
            LlmClient llmClient,
            StructuredResponseParser parser,
            StructuredValidator validator,
            StructuredRetryPolicy retryPolicy
    ) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
    }

    public StructuredTest generateValidated(LlmRequest baseRequest, GenerationContext context) {
        Objects.requireNonNull(baseRequest, "baseRequest must not be null");
        Objects.requireNonNull(context, "context must not be null");

        LlmRequest request = baseRequest;
        int retriesUsed = 0;

        while (true) {
            String raw = llmClient.generateRaw(request);

            StructuredTest parsed;
            try {
                parsed = parser.parse(raw);
            } catch (StructuredResponseException ex) {
                if (!retryPolicy.shouldRetry(ex.errorType(), retriesUsed)) {
                    throw new StructuredGenerationException(ex.errorType(), ex.getMessage(), ex);
                }
                retriesUsed++;
                request = withRetryHint(baseRequest, retryPolicy.retryHint(ex.errorType(), ex.getMessage()));
                continue;
            }

            ValidationResult result = validator.validate(parsed, context);
            if (result.valid()) {
                return parsed;
            }

            if (!retryPolicy.shouldRetry(result.errorType(), retriesUsed)) {
                throw new StructuredGenerationException(result.errorType(), result.message());
            }

            retriesUsed++;
            request = withRetryHint(baseRequest, retryPolicy.retryHint(result.errorType(), result.message()));
        }
    }

    private static LlmRequest withRetryHint(LlmRequest request, String retryHint) {
        String notes = request.projectTestPatternNotes();
        String updatedNotes = notes == null || notes.isBlank()
                ? RETRY_NOTES_PREFIX + retryHint
                : notes + "\n" + RETRY_NOTES_PREFIX + retryHint;

        return new LlmRequest(
                request.targetClassFqcn(),
                request.targetClassSource(),
                request.relatedSources(),
                request.testFramework(),
                updatedNotes,
                request.mode(),
                request.failureContext(),
                request.tuning(),
                request.timeout(),
                request.metadata()
        );
    }
}
