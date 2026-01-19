package io.testsmith.plugin.agent.selection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class SelectionState {
    private String lastSelectedClass;
    private int sameClassRepeatCount;
    private final Map<String, Integer> blacklist = new HashMap<>();
    private String snapshotFingerprint;
    private final Map<String, Integer> lastMissedMetricByClass = new HashMap<>();

    public String lastSelectedClass() {
        return lastSelectedClass;
    }

    public int sameClassRepeatCount() {
        return sameClassRepeatCount;
    }

    public Map<String, Integer> blacklist() {
        return Collections.unmodifiableMap(blacklist);
    }

    public String snapshotFingerprint() {
        return snapshotFingerprint;
    }

    public Integer lastMissedMetricFor(String className) {
        return lastMissedMetricByClass.get(className);
    }

    public void updateSnapshotFingerprint(String snapshotFingerprint) {
        this.snapshotFingerprint = snapshotFingerprint;
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

    public void blacklist(String className, int iterations) {
        blacklist.put(className, iterations);
    }

    public boolean isBlacklisted(String className) {
        Integer remaining = blacklist.get(className);
        return remaining != null && remaining > 0;
    }

    public void decrementBlacklist() {
        blacklist.replaceAll((key, value) -> value - 1);
        blacklist.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    public void setLastSelectedClass(String lastSelectedClass) {
        this.lastSelectedClass = lastSelectedClass;
    }

    public void setSameClassRepeatCount(int sameClassRepeatCount) {
        this.sameClassRepeatCount = sameClassRepeatCount;
    }

    public void setLastMissedMetricFor(String className, int missedMetric) {
        lastMissedMetricByClass.put(className, missedMetric);
    }
}
