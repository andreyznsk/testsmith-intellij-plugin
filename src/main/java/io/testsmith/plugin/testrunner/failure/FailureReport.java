package io.testsmith.plugin.testrunner.failure;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FailureReport(
        FailureKind kind,
        String summary,
        String rootCause,
        List<String> failingTests,
        List<String> evidence,
        Map<String, String> hints
) {
    public FailureReport {
        Objects.requireNonNull(kind, "kind must not be null");
        summary = summary == null ? "" : summary;
        rootCause = rootCause == null ? "" : rootCause;
        failingTests = failingTests == null ? List.of() : List.copyOf(failingTests);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        hints = hints == null ? Map.of() : Map.copyOf(hints);
    }

    public static FailureReport none() {
        return new FailureReport(FailureKind.NONE, "", "", List.of(), List.of(), Map.of());
    }
}
