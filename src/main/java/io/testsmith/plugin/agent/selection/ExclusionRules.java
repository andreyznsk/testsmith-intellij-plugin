package io.testsmith.plugin.agent.selection;

import io.testsmith.plugin.agent.coverage.model.ClassCoverage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ExclusionRules {
    private final List<String> excludedPackages;
    private final List<String> excludedClasses;

    public ExclusionRules(List<String> excludedPackages, List<String> excludedClasses) {
        this.excludedPackages = normalizePackages(excludedPackages);
        this.excludedClasses = normalizeClasses(excludedClasses);
    }

    public static ExclusionRules empty() {
        return new ExclusionRules(List.of(), List.of());
    }

    public List<String> excludedPackages() {
        return excludedPackages;
    }

    public List<String> excludedClasses() {
        return excludedClasses;
    }

    public boolean isExcluded(ClassCoverage coverage) {
        Objects.requireNonNull(coverage, "coverage must not be null");
        return match(coverage.className()).isPresent();
    }

    public boolean isExcluded(String fqcn) {
        return match(fqcn).isPresent();
    }

    public Optional<ExclusionMatch> match(ClassCoverage coverage) {
        Objects.requireNonNull(coverage, "coverage must not be null");
        return match(coverage.className());
    }

    public Optional<ExclusionMatch> match(String fqcn) {
        Objects.requireNonNull(fqcn, "fqcn must not be null");
        for (String excludedClass : excludedClasses) {
            if (excludedClass.equals(fqcn)) {
                return Optional.of(new ExclusionMatch(ExclusionMatch.Type.CLASS, excludedClass));
            }
        }
        String packageName = packageNameOf(fqcn);
        for (String excludedPackage : excludedPackages) {
            if (matchesPackage(packageName, excludedPackage)) {
                return Optional.of(new ExclusionMatch(ExclusionMatch.Type.PACKAGE, excludedPackage));
            }
        }
        return Optional.empty();
    }

    private static List<String> normalizeClasses(List<String> raw) {
        Objects.requireNonNull(raw, "excluded class list must not be null");
        return normalizeList(raw, false);
    }

    private static List<String> normalizePackages(List<String> raw) {
        Objects.requireNonNull(raw, "excluded package list must not be null");
        return normalizeList(raw, true);
    }

    private static List<String> normalizeList(List<String> raw, boolean stripPackageSuffix) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (stripPackageSuffix) {
                if (trimmed.endsWith(".*")) trimmed = trimmed.substring(0, trimmed.length() - 2).trim();
                if (trimmed.endsWith(".")) trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            }
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private static boolean matchesPackage(String packageName, String rule) {
        return packageName.equals(rule) || packageName.startsWith(rule + ".");
    }

    private static String packageNameOf(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        if (lastDot <= 0) {
            return "";
        }
        return fqcn.substring(0, lastDot);
    }
}
