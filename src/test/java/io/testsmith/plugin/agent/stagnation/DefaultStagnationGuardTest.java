package io.testsmith.plugin.agent.stagnation;

import io.testsmith.plugin.agent.coverage.diff.CoverageDiff;
import io.testsmith.plugin.agent.coverage.model.ClassCoverage;
import io.testsmith.plugin.agent.coverage.model.ClassId;
import io.testsmith.plugin.agent.coverage.model.CoverageSnapshot;
import io.testsmith.plugin.agent.coverage.model.CoverageSummary;
import io.testsmith.plugin.agent.coverage.model.PackageName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultStagnationGuardTest {
    @Test
    void noCoverageProgress_stopsAfterNFullSuiteRuns() {
        StagnationTuning tuning = new StagnationTuning(2, 5);
        AgentIterationContext context = new AgentIterationContext(tuning);
        StagnationState state = new StagnationState();
        DefaultStagnationGuard guard = new DefaultStagnationGuard();
        ClassCoverage candidate = coverage("com.example.Foo", 0, 5, 0, 5);

        CoverageDiff noProgress = diff(2, 2, 2, 2);

        StagnationDecision first = guard.evaluate(context, state, candidate, Optional.of(noProgress));
        StagnationDecision second = guard.evaluate(context, state, candidate, Optional.of(noProgress));
        StagnationDecision third = guard.evaluate(context, state, candidate, Optional.of(noProgress));

        assertFalse(first.isStagnating());
        assertFalse(second.isStagnating());
        assertTrue(third.isStagnating());
        assertEquals(StagnationReason.NO_COVERAGE_PROGRESS, third.reason());
    }

    @Test
    void coverageProgress_resetsNoProgressCounter() {
        StagnationTuning tuning = new StagnationTuning(2, 5);
        AgentIterationContext context = new AgentIterationContext(tuning);
        StagnationState state = new StagnationState();
        DefaultStagnationGuard guard = new DefaultStagnationGuard();
        ClassCoverage candidate = coverage("com.example.Foo", 0, 5, 0, 5);

        CoverageDiff noProgress = diff(2, 2, 2, 2);
        CoverageDiff progress = diff(2, 2, 3, 1);

        guard.evaluate(context, state, candidate, Optional.of(noProgress));
        guard.evaluate(context, state, candidate, Optional.of(noProgress));
        StagnationDecision withProgress = guard.evaluate(context, state, candidate, Optional.of(progress));

        assertFalse(withProgress.isStagnating());
        assertEquals(0, state.noProgressCount());

        guard.evaluate(context, state, candidate, Optional.of(noProgress));
        assertEquals(1, state.noProgressCount());
    }

    @Test
    void sameClassRepeatsWithoutMissedImprovement_stopsAfterMRepeats() {
        StagnationTuning tuning = new StagnationTuning(5, 2);
        AgentIterationContext context = new AgentIterationContext(tuning);
        StagnationState state = new StagnationState();
        DefaultStagnationGuard guard = new DefaultStagnationGuard();
        ClassCoverage candidate = coverage("com.example.Foo", 0, 5, 0, 5);

        StagnationDecision first = guard.evaluate(context, state, candidate, Optional.empty());
        StagnationDecision second = guard.evaluate(context, state, candidate, Optional.empty());
        StagnationDecision third = guard.evaluate(context, state, candidate, Optional.empty());

        assertFalse(first.isStagnating());
        assertFalse(second.isStagnating());
        assertTrue(third.isStagnating());
        assertEquals(StagnationReason.SAME_CLASS_THRASHING, third.reason());
    }

    @Test
    void sameClassRepeatsWithImprovement_doesNotStop() {
        StagnationTuning tuning = new StagnationTuning(5, 1);
        AgentIterationContext context = new AgentIterationContext(tuning);
        StagnationState state = new StagnationState();
        DefaultStagnationGuard guard = new DefaultStagnationGuard();

        ClassCoverage first = coverage("com.example.Foo", 0, 6, 0, 6);
        ClassCoverage improved = coverage("com.example.Foo", 0, 4, 0, 4);

        guard.evaluate(context, state, first, Optional.empty());
        StagnationDecision decision = guard.evaluate(context, state, improved, Optional.empty());

        assertFalse(decision.isStagnating());
    }

    @Test
    void sameClassRepeatsWithRegression_stops() {
        StagnationTuning tuning = new StagnationTuning(5, 2);
        AgentIterationContext context = new AgentIterationContext(tuning);
        StagnationState state = new StagnationState();
        DefaultStagnationGuard guard = new DefaultStagnationGuard();

        ClassCoverage first = coverage("com.example.Foo", 0, 4, 0, 4);
        ClassCoverage worse = coverage("com.example.Foo", 0, 6, 0, 6);

        guard.evaluate(context, state, first, Optional.empty());
        guard.evaluate(context, state, worse, Optional.empty());
        StagnationDecision third = guard.evaluate(context, state, worse, Optional.empty());

        assertTrue(third.isStagnating());
        assertEquals(StagnationReason.SAME_CLASS_THRASHING, third.reason());
    }

    @Test
    void classChange_resetsRepeatCounter() {
        StagnationTuning tuning = new StagnationTuning(5, 2);
        AgentIterationContext context = new AgentIterationContext(tuning);
        StagnationState state = new StagnationState();
        DefaultStagnationGuard guard = new DefaultStagnationGuard();

        ClassCoverage first = coverage("com.example.Foo", 0, 5, 0, 5);
        ClassCoverage second = coverage("com.example.Bar", 0, 5, 0, 5);

        guard.evaluate(context, state, first, Optional.empty());
        guard.evaluate(context, state, second, Optional.empty());

        assertEquals("com.example.Bar", state.lastSelectedClass());
        assertEquals(1, state.sameClassRepeatCount());
    }

    @Test
    void notStagnating_reasonThrows() {
        assertThrows(IllegalStateException.class, () -> StagnationDecision.notStagnating().reason());
        assertThrows(IllegalStateException.class, () -> StagnationDecision.notStagnating().details());
    }

    private static ClassCoverage coverage(String className, int lineCovered, int lineMissed,
                                          int instrCovered, int instrMissed) {
        return new ClassCoverage(
                new ClassId(className),
                new PackageName("com.example"),
                lineCovered,
                lineMissed,
                instrCovered,
                instrMissed,
                0,
                0,
                lineMissed <= 0 ? Set.of() : Set.of(1),
                className.substring(className.lastIndexOf('.') + 1) + ".java"
        );
    }

    private static CoverageDiff diff(int beforeCovered, int beforeMissed, int afterCovered, int afterMissed) {
        return new CoverageDiff(snapshot(beforeCovered, beforeMissed), snapshot(afterCovered, afterMissed));
    }

    private static CoverageSnapshot snapshot(int covered, int missed) {
        int total = covered + missed;
        ClassCoverage coverage = new ClassCoverage(
                new ClassId("com.example.Foo"),
                new PackageName("com.example"),
                covered,
                missed,
                covered,
                missed,
                0,
                0,
                missed <= 0 ? Set.of() : Set.of(1),
                "Foo.java"
        );
        return new CoverageSnapshot(
                Instant.parse("2024-01-01T00:00:00Z"),
                new CoverageSummary(total, covered, missed),
                Map.of(coverage.classId(), coverage)
        );
    }
}
