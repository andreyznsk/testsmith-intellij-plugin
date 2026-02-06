package io.testsmith.plugin.agent.stagnation;

import io.testsmith.plugin.agent.coverage.diff.CoverageDiff;
import io.testsmith.plugin.agent.coverage.model.ClassCoverage;

import java.util.Objects;
import java.util.Optional;

/**
 * Default stagnation policy for autonomous runs.
 *
 * <p>Contracts:</p>
 * <ul>
 *   <li>NO_COVERAGE_PROGRESS triggers when consecutive full-suite runs without progress exceed
 *   {@link StagnationTuning#maxNoProgressFullSuiteRuns()} (exactly N does not stop).</li>
 *   <li>SAME_CLASS_THRASHING triggers when the same class repeats more than
 *   {@link StagnationTuning#maxSameClassRepeats()} times without a decrease in the missed metric.</li>
 * </ul>
 */
public final class DefaultStagnationGuard implements StagnationGuard {
    @Override
    public StagnationDecision evaluate(
            AgentIterationContext context,
            StagnationState state,
            ClassCoverage candidate,
            Optional<CoverageDiff> lastFullSuiteDiff) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(lastFullSuiteDiff, "lastFullSuiteDiff must not be null");

        StagnationTuning tuning = context.tuning();
        boolean noProgressTriggered = false;
        String noProgressDetails = "";

        if (lastFullSuiteDiff.isPresent()) {
            CoverageDiff diff = lastFullSuiteDiff.get();
            if (diff.hasProgress()) {
                state.resetNoProgressCount();
            } else {
                state.recordNoProgress();
                if (state.noProgressCount() > tuning.maxNoProgressFullSuiteRuns()) {
                    noProgressTriggered = true;
                    noProgressDetails = "criterion=NO_COVERAGE_PROGRESS"
                            + ", noProgressCount=" + state.noProgressCount()
                            + ", maxNoProgressFullSuiteRuns=" + tuning.maxNoProgressFullSuiteRuns()
                            + ", deltaCoveredLines=" + diff.deltaCoveredLines()
                            + ", deltaMissedLines=" + diff.deltaMissedLines();
                }
            }
        }

        String className = candidate.className();
        Integer previousMissed = state.lastMissedMetricFor(className);
        boolean sameClass = className.equals(state.lastSelectedClass());
        // Compute would-be repeat count before mutating state.
        int wouldBeRepeatCount = sameClass ? state.sameClassRepeatCount() + 1 : 1;
        int currentMissed = missedMetric(candidate);

        boolean sameClassTriggered = false;
        String sameClassDetails = "";

        if (sameClass && wouldBeRepeatCount > tuning.maxSameClassRepeats() && previousMissed != null) {
            if (currentMissed >= previousMissed) {
                sameClassTriggered = true;
                sameClassDetails = "criterion=SAME_CLASS_THRASHING"
                        + ", className=" + className
                        + ", repeatCount=" + wouldBeRepeatCount
                        + ", maxSameClassRepeats=" + tuning.maxSameClassRepeats()
                        + ", previousMissed=" + previousMissed
                        + ", currentMissed=" + currentMissed;
            }
        }

        state.recordSelection(className);
        state.updateMissedMetric(className, currentMissed);

        if (noProgressTriggered) {
            return StagnationDecision.stagnating(StagnationReason.NO_COVERAGE_PROGRESS, noProgressDetails);
        }
        if (sameClassTriggered) {
            return StagnationDecision.stagnating(StagnationReason.SAME_CLASS_THRASHING, sameClassDetails);
        }
        return StagnationDecision.notStagnating();
    }

    private int missedMetric(ClassCoverage candidate) {
        // Use line missed as the primary metric for thrashing detection.
        return candidate.lineMissed();
    }
}
