package io.testsmith.plugin.agent;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

public record AgentEvent(@NotNull AgentEventType type, @NotNull String message, @NotNull Instant at) {
    public AgentEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(at, "at");
    }
}
