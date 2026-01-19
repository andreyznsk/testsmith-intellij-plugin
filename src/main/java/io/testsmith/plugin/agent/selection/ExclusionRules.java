package io.testsmith.plugin.agent.selection;

import io.testsmith.plugin.agent.coverage.model.ClassCoverage;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ExclusionRules {
    private final List<Pattern> includeClassPatterns;
    private final List<Pattern> excludeClassPatterns;
    private final List<Pattern> includePackagePatterns;
    private final List<Pattern> excludePackagePatterns;

    public ExclusionRules(
            List<String> includeClassPatterns,
            List<String> excludeClassPatterns,
            List<String> includePackagePatterns,
            List<String> excludePackagePatterns) {
        this.includeClassPatterns = compilePatterns(includeClassPatterns);
        this.excludeClassPatterns = compilePatterns(excludeClassPatterns);
        this.includePackagePatterns = compilePatterns(includePackagePatterns);
        this.excludePackagePatterns = compilePatterns(excludePackagePatterns);
    }

    public static ExclusionRules empty() {
        return new ExclusionRules(List.of(), List.of(), List.of(), List.of());
    }

    public boolean isExcluded(ClassCoverage coverage) {
        Objects.requireNonNull(coverage, "coverage must not be null");
        String className = coverage.className();
        String packageName = coverage.packageNameValue();

        if (!includeClassPatterns.isEmpty() || !includePackagePatterns.isEmpty()) {
            boolean matchesInclude = matchesAny(includeClassPatterns, className)
                    || matchesAny(includePackagePatterns, packageName);
            if (!matchesInclude) {
                return true;
            }
        }

        return matchesAny(excludeClassPatterns, className)
                || matchesAny(excludePackagePatterns, packageName);
    }

    private static List<Pattern> compilePatterns(List<String> raw) {
        Objects.requireNonNull(raw, "pattern list must not be null");
        return raw.stream()
                .filter(Objects::nonNull)
                .map(Pattern::compile)
                .toList();
    }

    private static boolean matchesAny(List<Pattern> patterns, String value) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }
}
