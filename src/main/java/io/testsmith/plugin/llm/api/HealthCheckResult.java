package io.testsmith.plugin.llm.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record HealthCheckResult(
        @NotNull Status status,
        @NotNull String userMessage,
        @Nullable String technicalCode,
        @NotNull String providerId,
        @Nullable Long latencyMs
) {
    public enum Status {
        OK,
        FAILED
    }

    public HealthCheckResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(userMessage, "userMessage");
        Objects.requireNonNull(providerId, "providerId");
    }

    public static @NotNull HealthCheckResult ok(
            @NotNull String providerId,
            @NotNull String userMessage,
            long latencyMs
    ) {
        return new HealthCheckResult(Status.OK, userMessage, null, providerId, Math.max(0L, latencyMs));
    }

    public static @NotNull HealthCheckResult failed(
            @NotNull String providerId,
            @NotNull String userMessage,
            @Nullable String technicalCode,
            @Nullable Long latencyMs
    ) {
        return new HealthCheckResult(Status.FAILED, userMessage, technicalCode, providerId, latencyMs);
    }
}
