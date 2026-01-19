package io.testsmith.plugin.agent.coverage.model;

import java.util.Objects;

public record ClassId(String value) {
    public ClassId {
        Objects.requireNonNull(value, "classId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("classId value must not be blank");
        }
    }
}
