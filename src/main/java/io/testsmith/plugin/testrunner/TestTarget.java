package io.testsmith.plugin.testrunner;

import java.util.Objects;

public final class TestTarget {
    private final String className;
    private final String methodName;

    public TestTarget(String className, String methodName) {
        this.className = requireNonBlank(className, "className");
        this.methodName = normalize(methodName);
    }

    public String className() {
        return className;
    }

    public String methodName() {
        return methodName;
    }

    public String toMavenFilter() {
        if (methodName == null) {
            return className;
        }
        return className + "#" + methodName;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
