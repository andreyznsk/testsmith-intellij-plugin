package io.testsmith.plugin.settings;

public enum ExecutionMode {
    MANUAL("Manual"),
    AUTONOMOUS("Autonomous");

    private final String displayName;

    ExecutionMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
