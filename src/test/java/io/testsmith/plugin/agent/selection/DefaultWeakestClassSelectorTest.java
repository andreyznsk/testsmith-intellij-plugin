package io.testsmith.plugin.agent.selection;

import io.testsmith.plugin.agent.coverage.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class DefaultWeakestClassSelectorTest {
    @Test
    void picksClassWithHighestMissedLines() {
        ClassCoverage first = coverage("com.example.First", "com.example", 1, 5, 4, 6);
        ClassCoverage second = coverage("com.example.Second", "com.example", 1, 2, 3, 3);

        SelectionContext context = new SelectionContext(ExclusionRules.empty(), SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector();

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(first, second)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.First", selected.get().className());
    }

    @Test
    void tieOnMissedLinesPicksWorseCoverageRatio() {
        ClassCoverage worseCoverage = coverage("com.example.A", "com.example", 5, 5, 4, 6);
        ClassCoverage betterCoverage = coverage("com.example.B", "com.example", 9, 5, 4, 6);

        SelectionContext context = new SelectionContext(ExclusionRules.empty(), SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector();

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(betterCoverage, worseCoverage)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.A", selected.get().className());
    }

    @Test
    void excludesUserClasses() {
        ClassCoverage weakestExcluded = coverage("com.example.Invoice", "com.example", 1, 6, 2, 2);
        ClassCoverage nextWeakest = coverage("com.example.Billing", "com.example", 1, 4, 2, 2);
        ClassCoverage strongest = coverage("com.example.Pricing", "com.example", 2, 1, 2, 2);

        ExclusionRules rules = new ExclusionRules(List.of(), List.of("com.example.Invoice"));
        SelectionContext context = new SelectionContext(rules, SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector();

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(weakestExcluded, nextWeakest, strongest)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.Billing", selected.get().className());
    }

    @Test
    void excludesTestsAndTestPackages() {
        ClassCoverage testClass = coverage("com.example.service.UserServiceTest", "com.example.service", 0, 10, 0, 10);
        ClassCoverage domainClass = coverage("com.example.service.UserService", "com.example.service", 0, 2, 0, 2);

        SelectionContext context = new SelectionContext(ExclusionRules.empty(), SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector();

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(testClass, domainClass)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.service.UserService", selected.get().className());
    }

    @Test
    void returnsEmptyWhenAllExcluded() {
        ClassCoverage first = coverage("com.example.First", "com.example", 1, 5, 2, 2);
        ClassCoverage second = coverage("com.example.Second", "com.example", 1, 2, 2, 2);

        ExclusionRules rules = new ExclusionRules(List.of(), List.of("com.example.First", "com.example.Second"));
        SelectionContext context = new SelectionContext(rules, SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector();

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(first, second)), context);

        assertTrue(selected.isEmpty());
    }

    @Test
    void fallsBackToInstructionCoverageWhenLineDataMissing() {
        ClassCoverage noLines = coverage("com.example.NoLines", "com.example", -1, -1, 0, 10);
        ClassCoverage lines = coverage("com.example.Lines", "com.example", 2, 2, 2, 2);

        SelectionContext context = new SelectionContext(ExclusionRules.empty(), SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector();

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(noLines, lines)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.NoLines", selected.get().className());
    }

    @Test
    void stagnationGuardBlacklistsRepeatedNonImprovingTarget() {
        ClassCoverage stagnant = coverage("com.example.A", "com.example", 0, 5, 0, 5);
        ClassCoverage nextBest = coverage("com.example.B", "com.example", 0, 4, 0, 4);

        SelectionState state = new SelectionState();
        state.setLastSelectedClass("com.example.A");
        state.setSameClassRepeatCount(2);
        state.setLastMissedMetricFor("com.example.A", 5);

        SelectionContext context = new SelectionContext(ExclusionRules.empty(), SelectionTuning.defaults(), state);
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector();

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(stagnant, nextBest)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.B", selected.get().className());
        assertEquals(3, state.blacklist().get("com.example.A"));

    }

    private static ClassCoverage coverage(String className, String packageName, int lineCovered, int lineMissed,
                                          int instrCovered, int instrMissed) {
        return coverage(className, packageName, lineCovered, lineMissed, instrCovered, instrMissed, 0, 0);
    }

    @SuppressWarnings("SameParameterValue")
    private static ClassCoverage coverage(String className, String packageName, int lineCovered, int lineMissed,
                                          int instrCovered, int instrMissed, int branchCovered, int branchMissed) {
        return new ClassCoverage(
                new ClassId(className),
                new PackageName(packageName),
                lineCovered,
                lineMissed,
                instrCovered,
                instrMissed,
                branchCovered,
                branchMissed,
                missedLines(lineMissed),
                className.substring(className.lastIndexOf('.') + 1) + ".java"
        );
    }

    private static Set<Integer> missedLines(int lineMissed) {
        if (lineMissed <= 0) {
            return Set.of();
        }
        return IntStream.rangeClosed(1, lineMissed).boxed().collect(Collectors.toCollection(HashSet::new));
    }

    private static CoverageSnapshot snapshot(List<ClassCoverage> classes) {
        Map<ClassId, ClassCoverage> map = new HashMap<>();
        int total = 0;
        int covered = 0;
        int missed = 0;
        for (ClassCoverage coverage : classes) {
            map.put(coverage.classId(), coverage);
            if (coverage.lineCovered() >= 0 && coverage.lineMissed() >= 0) {
                total += coverage.lineCovered() + coverage.lineMissed();
                covered += coverage.lineCovered();
                missed += coverage.lineMissed();
            }
        }
        return new CoverageSnapshot(
                Instant.parse("2024-01-01T00:00:00Z"),
                new CoverageSummary(total, covered, missed),
                map
        );
    }
}
