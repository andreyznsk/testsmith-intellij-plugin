package io.testsmith.plugin.agent.coverage.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassCoverageTest {
    @Test
    void invariantsRejectInvalidTotals() {
        assertThrows(IllegalArgumentException.class, () -> new ClassCoverage(
                new ClassId("com.example.Foo"),
                new PackageName("com.example"),
                5,
                3,
                0,
                0,
                0,
                0,
                Set.of(1),
                null
        ));
    }

    @Test
    void invariantsRejectMismatchedMissedLineNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new ClassCoverage(
                new ClassId("com.example.Foo"),
                new PackageName("com.example"),
                2,
                0,
                1,
                1,
                0,
                0,
                Set.of(1),
                null
        ));
    }

    @Test
    void defensiveCopyProtectsMissedLineNumbers() {
        Set<Integer> missedLines = new HashSet<>();
        missedLines.add(1);
        missedLines.add(2);

        ClassCoverage coverage = new ClassCoverage(
                new ClassId("com.example.Foo"),
                new PackageName("com.example"),
                0,
                2,
                1,
                1,
                0,
                0,
                missedLines,
                "Foo.java"
        );

        missedLines.add(3);

        assertFalse(coverage.missedLineNumbers().contains(3));
    }

    @Test
    void allowsMissingLineCoverageWithEmptyMissedLines() {
        new ClassCoverage(
                new ClassId("com.example.NoLines"),
                new PackageName("com.example"),
                -1,
                -1,
                2,
                4,
                0,
                0,
                Set.of(),
                null
        );
    }
}
