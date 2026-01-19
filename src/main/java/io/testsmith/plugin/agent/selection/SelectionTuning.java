package io.testsmith.plugin.agent.selection;

import java.util.List;
import java.util.Objects;

public record SelectionTuning(
        int maxSameClassRepeats,
        int blacklistIterations,
        List<String> positivePackageKeywords,
        List<String> negativePackageKeywords) {
    public SelectionTuning {
        if (maxSameClassRepeats < 1) {
            throw new IllegalArgumentException("maxSameClassRepeats must be at least 1");
        }
        if (blacklistIterations < 1) {
            throw new IllegalArgumentException("blacklistIterations must be at least 1");
        }
        Objects.requireNonNull(positivePackageKeywords, "positivePackageKeywords must not be null");
        Objects.requireNonNull(negativePackageKeywords, "negativePackageKeywords must not be null");
        positivePackageKeywords = List.copyOf(positivePackageKeywords);
        negativePackageKeywords = List.copyOf(negativePackageKeywords);
    }

    public static SelectionTuning defaults() {
        return new SelectionTuning(
                2,
                3,
                List.of("domain", "core", "service", "logic"),
                List.of("dto", "config", "model", "entity", "generated", "api")
        );
    }
}
