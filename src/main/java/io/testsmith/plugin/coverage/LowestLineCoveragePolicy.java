package io.testsmith.plugin.coverage;

import java.util.Comparator;
import java.util.Optional;

public final class LowestLineCoveragePolicy implements TargetSelectionPolicy {
    private final CoverageFilter filter;

    public LowestLineCoveragePolicy(CoverageFilter filter) {
        this.filter = filter;
    }

    @Override
    public Optional<ClassCoverage> select(CoverageSnapshot snapshot) {
        return snapshot.packages().values().stream()
            .flatMap(pkg -> pkg.classes().values().stream())
            .filter(filter::include)
            .min(Comparator
                .comparingDouble((ClassCoverage coverage) -> coverage.lineCoverage().ratio())
                .thenComparing(Comparator.comparingInt((ClassCoverage coverage) -> coverage.lineCoverage().missed())
                    .reversed()));
    }
}
