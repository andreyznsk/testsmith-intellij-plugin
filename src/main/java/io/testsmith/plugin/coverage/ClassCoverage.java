package io.testsmith.plugin.coverage;

public record ClassCoverage(
    String className,
    LineCoverage lineCoverage
) {}
