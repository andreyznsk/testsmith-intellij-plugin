package io.testsmith.plugin.settings;

public enum BuildToolMode {
    AUTO("Auto"),
    MAVEN("Maven"),
    GRADLE("Gradle");

    private final String displayName;

    BuildToolMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
