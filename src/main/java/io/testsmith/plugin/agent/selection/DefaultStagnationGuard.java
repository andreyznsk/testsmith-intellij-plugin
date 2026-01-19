package io.testsmith.plugin.agent.selection;

import io.testsmith.plugin.agent.coverage.model.ClassCoverage;

import java.util.List;
import java.util.Optional;

public final class DefaultStagnationGuard {
    private final DefaultCandidateRanker ranker;

    public DefaultStagnationGuard(DefaultCandidateRanker ranker) {
        this.ranker = ranker;
    }

    public Optional<ClassCoverage> select(List<ClassCoverage> rankedCandidates, SelectionContext context) {
        if (rankedCandidates.isEmpty()) {
            return Optional.empty();
        }

        SelectionState state = context.state();
        SelectionTuning tuning = context.tuning();

        for (ClassCoverage candidate : rankedCandidates) {
            if (shouldBlacklist(candidate, state, tuning)) {
                state.blacklist(candidate.className(), tuning.blacklistIterations());
                continue;
            }
            return Optional.of(candidate);
        }

        return Optional.empty();
    }

    private boolean shouldBlacklist(ClassCoverage candidate, SelectionState state, SelectionTuning tuning) {
        String className = candidate.className();
        if (!className.equals(state.lastSelectedClass())) {
            return false;
        }
        if (state.sameClassRepeatCount() <= tuning.maxSameClassRepeats()) {
            return false;
        }
        Integer previousMissed = state.lastMissedMetricFor(className);
        if (previousMissed == null) {
            return false;
        }
        int currentMissed = ranker.missedMetric(candidate);
        return currentMissed >= previousMissed;
    }
}
