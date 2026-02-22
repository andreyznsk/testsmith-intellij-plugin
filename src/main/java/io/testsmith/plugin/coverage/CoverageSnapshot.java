package io.testsmith.plugin.coverage;

import java.util.Map;

public record CoverageSnapshot(
        Map<String, PackageCoverage> packages
) {
}
