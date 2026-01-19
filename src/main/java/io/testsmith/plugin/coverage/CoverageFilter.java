package io.testsmith.plugin.coverage;

public interface CoverageFilter {
    boolean include(ClassCoverage coverage);
}
