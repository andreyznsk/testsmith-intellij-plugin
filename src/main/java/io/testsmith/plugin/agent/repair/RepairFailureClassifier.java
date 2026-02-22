package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.testrunner.failure.FailureReport;
import io.testsmith.plugin.testrunner.model.TestExecutionResult;
import io.testsmith.plugin.testrunner.model.TestExecutionStatus;

import java.util.Locale;
import java.util.Objects;

public final class RepairFailureClassifier {
    private static final String JACOCO_MISSING = "jacoco xml not found";

    public RepairFailureType classify(TestExecutionResult failure) {
        Objects.requireNonNull(failure, "failure must not be null");

        if (failure.status() == TestExecutionStatus.TIMEOUT) {
            return RepairFailureType.TIMEOUT;
        }

        String corpus = normalize(buildCorpus(failure));

        if (failure.status() == TestExecutionStatus.INFRASTRUCTURE_ERROR) {
            return corpus.contains(JACOCO_MISSING)
                    ? RepairFailureType.MISSING_JACOCO_XML
                    : RepairFailureType.INFRASTRUCTURE;
        }

        if (failure.status() == TestExecutionStatus.COMPILATION_FAILED) {
            if (looksLikeMissingImport(corpus)) {
                return RepairFailureType.MISSING_IMPORT;
            }
            return RepairFailureType.COMPILATION;
        }

        if (failure.status() == TestExecutionStatus.TEST_FAILED) {
            if (looksLikeAssertionFailure(corpus)) {
                return RepairFailureType.ASSERTION;
            }
            return RepairFailureType.OTHER_TEST_FAILURE;
        }

        return RepairFailureType.UNKNOWN;
    }

    private static boolean looksLikeMissingImport(String corpus) {
        return corpus.contains("package ") && corpus.contains(" does not exist")
                || corpus.contains("cannot find symbol") && corpus.contains("import ")
                || corpus.contains("cannot resolve symbol") && corpus.contains("import ");
    }

    private static boolean looksLikeAssertionFailure(String corpus) {
        return corpus.contains("assertionfailederror")
                || corpus.contains("assertionerror")
                || corpus.contains("expected:") && corpus.contains("but was:")
                || corpus.contains("comparisonfailure");
    }

    private static String buildCorpus(TestExecutionResult result) {
        StringBuilder builder = new StringBuilder();
        append(builder, result.failureMessage());
        append(builder, result.stdout());
        append(builder, result.stderr());

        FailureReport report = result.failureReport();
        if (report != null) {
            append(builder, report.summary());
            append(builder, report.rootCause());
            for (String evidence : report.evidence()) {
                append(builder, evidence);
            }
        }
        return builder.toString();
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
