package io.testsmith.plugin.agent.selection;

import io.testsmith.plugin.agent.coverage.model.ClassCoverage;
import io.testsmith.plugin.agent.coverage.model.ClassId;
import io.testsmith.plugin.agent.coverage.model.CoverageSnapshot;
import io.testsmith.plugin.agent.coverage.model.CoverageSummary;
import io.testsmith.plugin.agent.coverage.model.PackageName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWeakestClassSelectorTest {
    @Test
    void picksClassWithHighestMissedLines() {
        ClassCoverage first = coverage("com.example.First", "com.example", 1, 5, 4, 6);
        ClassCoverage second = coverage("com.example.Second", "com.example", 1, 2, 3, 3);

        SelectionContext context = new SelectionContext(ExclusionRules.empty(), SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector(context);

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(first, second)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.First", selected.get().className());
    }

    @Test
    void tieOnMissedLinesPicksWorseCoverageRatio() {
        ClassCoverage worseCoverage = coverage("com.example.A", "com.example", 5, 5, 4, 6);
        ClassCoverage betterCoverage = coverage("com.example.B", "com.example", 9, 5, 4, 6);

        SelectionContext context = new SelectionContext(ExclusionRules.empty(), SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector(context);

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(betterCoverage, worseCoverage)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.A", selected.get().className());
    }

    @Test
    void excludesUserPatterns() {
        ClassCoverage excluded = coverage("com.example.Invoice", "com.example", 1, 4, 2, 2);
        ClassCoverage included = coverage("com.example.Billing", "com.example", 1, 1, 2, 2);

        ExclusionRules rules = new ExclusionRules(List.of(), List.of(".*Invoice"), List.of(), List.of());
        SelectionContext context = new SelectionContext(rules, SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector(context);

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(excluded, included)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.Billing", selected.get().className());
    }

    @Test
    void excludesTestsAndTestPackages() {
        ClassCoverage testClass = coverage("com.example.service.UserServiceTest", "com.example.service", 0, 10, 0, 10);
        ClassCoverage domainClass = coverage("com.example.service.UserService", "com.example.service", 0, 2, 0, 2);

        SelectionContext context = new SelectionContext(ExclusionRules.empty(), SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector(context);

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(testClass, domainClass)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.service.UserService", selected.get().className());
    }

    @Test
    void fallsBackToInstructionCoverageWhenLineDataMissing() {
        ClassCoverage noLines = coverage("com.example.NoLines", "com.example", -1, -1, 0, 10);
        ClassCoverage lines = coverage("com.example.Lines", "com.example", 2, 2, 2, 2);

        SelectionContext context = new SelectionContext(ExclusionRules.empty(), SelectionTuning.defaults(), new SelectionState());
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector(context);

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
        DefaultWeakestClassSelector selector = new DefaultWeakestClassSelector(context);

        Optional<ClassCoverage> selected = selector.select(snapshot(List.of(stagnant, nextBest)), context);

        assertTrue(selected.isPresent());
        assertEquals("com.example.B", selected.get().className());
        assertEquals(3, state.blacklist().get("com.example.A"));
    }

    private static ClassCoverage coverage(String className, String packageName, int lineCovered, int lineMissed,
                                          int instrCovered, int instrMissed) {
        return coverage(className, packageName, lineCovered, lineMissed, instrCovered, instrMissed, 0, 0);
    }

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
