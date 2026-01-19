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
                1,
                Set.of(1)
        ));
    }

    @Test
    void invariantsRejectMismatchedMissedLineNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new ClassCoverage(
                new ClassId("com.example.Foo"),
                new PackageName("com.example"),
                2,
                0,
                2,
                Set.of(1)
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
                2,
                0,
                2,
                missedLines
        );

        missedLines.add(3);

        assertFalse(coverage.missedLineNumbers().contains(3));
    }
}
