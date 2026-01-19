package io.testsmith.plugin.agent.coverage.model;

import java.util.Objects;

public record PackageName(String value) {
    public PackageName {
        Objects.requireNonNull(value, "package name must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("package name must not be blank");
        }
    }
}
