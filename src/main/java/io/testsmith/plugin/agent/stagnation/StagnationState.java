package io.testsmith.plugin.agent.stagnation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory state for stagnation detection during an agent session.
 */
public final class StagnationState {
    private int noProgressCount;
    private String lastSelectedClass;
    private int sameClassRepeatCount;
    private final Map<String, Integer> lastMissedMetricByClass = new HashMap<>();

    public int noProgressCount() {
        return noProgressCount;
    }

    /**
     * Last selected class name, or null before the first selection.
     */
    public String lastSelectedClass() {
        return lastSelectedClass;
    }

    public int sameClassRepeatCount() {
        return sameClassRepeatCount;
    }

    public Integer lastMissedMetricFor(String className) {
        return lastMissedMetricByClass.get(className);
    }

    public void recordNoProgress() {
        noProgressCount++;
    }

    public void resetNoProgressCount() {
        noProgressCount = 0;
    }

    public void recordSelection(String className) {
        Objects.requireNonNull(className, "className must not be null");
        if (className.equals(lastSelectedClass)) {
            sameClassRepeatCount++;
        } else {
            lastSelectedClass = className;
            sameClassRepeatCount = 1;
        }
    }

    public void updateMissedMetric(String className, int missedMetric) {
        lastMissedMetricByClass.put(className, missedMetric);
    }
}
