package io.testsmith.plugin.coverage;

import java.util.Map;

public record PackageCoverage(
        String packageName,
        Map<String, ClassCoverage> classes
) {
}
