package io.testsmith.plugin.agent.selection;

import io.testsmith.plugin.agent.coverage.model.ClassCoverage;
import io.testsmith.plugin.agent.coverage.model.CoverageSnapshot;

import java.util.Optional;

public interface WeakestClassSelector {
    Optional<ClassCoverage> select(CoverageSnapshot snapshot, SelectionContext context);
}
