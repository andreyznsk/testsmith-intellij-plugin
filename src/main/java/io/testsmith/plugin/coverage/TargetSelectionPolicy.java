package io.testsmith.plugin.coverage;

import java.util.Optional;

public interface TargetSelectionPolicy {
    Optional<ClassCoverage> select(CoverageSnapshot snapshot);
}
