package io.testsmith.plugin.agent.selection;

import io.testsmith.plugin.agent.coverage.model.ClassCoverage;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class DefaultCandidateFilter {
    private static final Set<String> GENERATED_MARKERS = Set.of(
            "generated",
            "proto",
            "thrift",
            "avro",
            "jooq",
            "swagger",
            "openapi"
    );

    private final ExclusionRules exclusions;

    public DefaultCandidateFilter(ExclusionRules exclusions) {
        this.exclusions = Objects.requireNonNull(exclusions, "exclusions must not be null");
    }

    public boolean include(ClassCoverage coverage) {
        Objects.requireNonNull(coverage, "coverage must not be null");

        if (exclusions.isExcluded(coverage)) {
            return false;
        }

        String className = coverage.className();
        String packageName = coverage.packageNameValue();
        String lowerClassName = className.toLowerCase(Locale.ROOT);
        String lowerPackage = packageName.toLowerCase(Locale.ROOT);

        if (lowerClassName.endsWith("module-info") || lowerClassName.endsWith("package-info")) {
            return false;
        }

        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        if (isTestClass(simpleName, lowerClassName, lowerPackage)) {
            return false;
        }

        if (containsGeneratedMarker(lowerClassName) || containsGeneratedMarker(lowerPackage)) {
            return false;
        }

        boolean noLineMisses = coverage.lineMissed() == 0 || coverage.lineMissed() == -1;
        if (noLineMisses && coverage.instrMissed() == 0) {
            return false;
        }

        return true;
    }

    private static boolean isTestClass(String simpleName, String lowerClassName, String lowerPackage) {
        if (lowerPackage.contains(".test")) {
            return true;
        }
        if (lowerClassName.contains("e2e")) {
            return true;
        }
        return simpleName.endsWith("Test")
                || simpleName.endsWith("Tests")
                || simpleName.endsWith("IT")
                || simpleName.endsWith("IntegrationTest");
    }

    private static boolean containsGeneratedMarker(String value) {
        for (String marker : GENERATED_MARKERS) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
