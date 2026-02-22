package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.*;
import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.api.LlmTuning;
import io.testsmith.plugin.testrunner.failure.FailureReport;
import io.testsmith.plugin.testrunner.model.TestExecutionResult;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

public final class LlmTestRepairAgent implements TestRepairAgent {
    private static final String REPAIR_NOTES_PREFIX = "[repair-attempt] ";

    private final StructuredGenerationGateway generationGateway;
    private final RepairFailureClassifier failureClassifier;

    public LlmTestRepairAgent(StructuredGenerationGateway generationGateway) {
        this(generationGateway, new RepairFailureClassifier());
    }

    public LlmTestRepairAgent(
            StructuredGenerationGateway generationGateway,
            RepairFailureClassifier failureClassifier
    ) {
        this.generationGateway = Objects.requireNonNull(generationGateway, "generationGateway must not be null");
        this.failureClassifier = Objects.requireNonNull(failureClassifier, "failureClassifier must not be null");
    }

    @Override
    public RepairResult attemptRepair(
            StructuredTest originalTest,
            TestExecutionResult failure,
            RepairContext context
    ) {
        Objects.requireNonNull(originalTest, "originalTest must not be null");
        Objects.requireNonNull(failure, "failure must not be null");
        Objects.requireNonNull(context, "context must not be null");

        RepairFailureType failureType = failureClassifier.classify(failure);

        LlmRequest request = new LlmRequest(
                context.targetClassFqcn(),
                context.targetClassSource(),
                context.relatedSources(),
                context.testFramework(),
                withRepairHint(context.projectTestPatternNotes(), context.attemptNumber(), failureType),
                GenerationMode.FIX,
                buildFailureContext(originalTest, failure, failureType),
                deterministicTuning(),
                context.llmTimeout(),
                context.metadata()
        );

        GenerationContext generationContext = new GenerationContext(
                StructuredAction.REPAIR_TEST,
                context.targetClassFqcn(),
                StructuredTest.VERSION_1_1,
                context.discoveredAnnotations()
        );

        StructuredTest repaired;
        try {
            repaired = generationGateway.generateValidated(request, generationContext);
        } catch (StructuredGenerationException ex) {
            if (ex.errorType() == ValidationErrorType.INFRASTRUCTURE_REQUIRED) {
                return RepairResult.hardAbort("repair requested infrastructure; hard abort");
            }
            return RepairResult.rejected("repair response rejected: " + ex.getMessage());
        }

        String violation = validateInvariants(originalTest, repaired, context);
        if (!violation.isEmpty()) {
            return RepairResult.hardAbort(violation);
        }

        return RepairResult.repaired(repaired, "repair accepted");
    }

    private static String withRepairHint(String notes, int attemptNumber, RepairFailureType failureType) {
        String hint = REPAIR_NOTES_PREFIX + "attempt=" + attemptNumber + ", failureType=" + failureType;
        if (notes == null || notes.isBlank()) {
            return hint;
        }
        return notes + "\n" + hint;
    }

    private static LlmTuning deterministicTuning() {
        LlmTuning defaults = LlmTuning.defaults();
        return new LlmTuning(
                LlmTuning.DEFAULT_TEMPERATURE,
                defaults.topP(),
                defaults.repeatPenalty()
        );
    }

    private static String buildFailureContext(
            StructuredTest originalTest,
            TestExecutionResult failure,
            RepairFailureType failureType
    ) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("FailureClassification: " + failureType);
        joiner.add("ExecutionStatus: " + failure.status());
        joiner.add("FailureMessage: " + safe(failure.failureMessage()));

        joiner.add("OriginalStructuredTest:");
        joiner.add("version=" + originalTest.version());
        joiner.add("action=" + originalTest.action());
        joiner.add("targetClass=" + originalTest.targetClass());
        joiner.add("testClassName=" + originalTest.testClassName());
        joiner.add("imports=" + originalTest.imports());
        joiner.add("code=");
        joiner.add(originalTest.code());

        FailureReport failureReport = failure.failureReport();
        if (failureReport != null) {
            joiner.add("FailureReportKind: " + failureReport.kind());
            joiner.add("FailureRootCause: " + safe(failureReport.rootCause()));
            if (!failureReport.evidence().isEmpty()) {
                joiner.add("FailureEvidence:");
                for (String line : failureReport.evidence()) {
                    joiner.add(line);
                }
            }
        }

        if (!failure.stderr().isBlank()) {
            joiner.add("stderr:");
            joiner.add(failure.stderr());
        }
        if (!failure.stdout().isBlank()) {
            joiner.add("stdout:");
            joiner.add(failure.stdout());
        }

        return joiner.toString();
    }

    private static String validateInvariants(StructuredTest original, StructuredTest repaired, RepairContext context) {
        if (!StructuredAction.REPAIR_TEST.equals(repaired.action())) {
            return "hard abort: action must be REPAIR_TEST in repair mode";
        }
        if (!StructuredTest.VERSION_1_1.equals(repaired.version())) {
            return "hard abort: version must be 1.1 in repair mode";
        }
        if (!original.targetClass().equals(repaired.targetClass())) {
            return "hard abort: target class changed during repair";
        }
        if (repaired.requiresInfrastructure()) {
            return "hard abort: repaired test requires infrastructure";
        }

        String frameworkViolation = validateFramework(repaired.imports(), repaired.code(), context);
        if (!frameworkViolation.isEmpty()) {
            return frameworkViolation;
        }

        if (containsMutationHints(repaired.code()) || containsMutationHints(String.join("\n", repaired.assumptions()))) {
            return "hard abort: repaired output suggests project/build/infrastructure mutation";
        }

        return "";
    }

    private static String validateFramework(List<String> imports, String code, RepairContext context) {
        String corpus = String.join("\n", imports) + "\n" + code;
        String lower = corpus.toLowerCase(Locale.ROOT);

        boolean hasJUnit4 = lower.contains("org.junit.test") || lower.contains("org.junit.assert")
                || lower.contains("org.junit.runner") || lower.contains("org.junit.rules");
        boolean hasJUnit5 = lower.contains("org.junit.jupiter") || lower.contains("@extendwith");

        if (context.testFramework() == io.testsmith.plugin.llm.api.TestFramework.JUNIT5 && hasJUnit4) {
            return "hard abort: framework switch/mixing detected (expected JUNIT5)";
        }
        if (context.testFramework() == io.testsmith.plugin.llm.api.TestFramework.JUNIT4 && hasJUnit5) {
            return "hard abort: framework switch/mixing detected (expected JUNIT4)";
        }
        return "";
    }

    private static boolean containsMutationHints(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> markers = List.of(
                "pom.xml",
                "build.gradle",
                "settings.gradle",
                "dependencies",
                "plugin",
                "jacoco",
                "docker",
                "testcontainers",
                "add dependency",
                "modify production code",
                "change source"
        );
        for (String marker : markers) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
