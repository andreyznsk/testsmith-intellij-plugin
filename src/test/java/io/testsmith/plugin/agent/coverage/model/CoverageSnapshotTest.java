package io.testsmith.plugin.agent.coverage.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverageSnapshotTest {
    @Test
    void defensiveCopyProtectsClassesMap() {
        ClassCoverage coverage = new ClassCoverage(
                new ClassId("com.example.Foo"),
                new PackageName("com.example"),
                1,
                0,
                0,
                0,
                0,
                0,
                Set.of(),
                "Foo.java"
        );
        Map<ClassId, ClassCoverage> classes = new HashMap<>();
        classes.put(coverage.classId(), coverage);

        CoverageSnapshot snapshot = new CoverageSnapshot(
                Instant.parse("2024-01-01T00:00:00Z"),
                new CoverageSummary(1, 1, 0),
                classes
        );

        classes.put(new ClassId("com.example.Bar"), coverage);

        assertEquals(1, snapshot.classes().size());
    }
}
