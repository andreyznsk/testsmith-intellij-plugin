package io.testsmith.plugin.agent;

import io.testsmith.plugin.ui.model.AgentUiState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record AgentProgress(
        @NotNull AgentUiState state,
        int iteration,
        int maxIterations,
        double currentCoverage,
        double targetCoverage,
        @Nullable String currentClass,
        @NotNull String lastMessage,
        long startedAt,
        @Nullable Long lastUpdateAt
) {
    public AgentProgress {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (iteration < 0) {
            throw new IllegalArgumentException("iteration must be >= 0");
        }
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations must be >= 0");
        }
        if (state == AgentUiState.IDLE) {
            if (iteration != 0) {
                throw new IllegalArgumentException("iteration must be 0 in IDLE state");
            }
        } else {
            if (iteration < 1) {
                throw new IllegalArgumentException("iteration must be >= 1 outside IDLE");
            }
            if (maxIterations < 1) {
                throw new IllegalArgumentException("maxIterations must be >= 1 outside IDLE");
            }
        }

        currentCoverage = clamp0to100(round1(currentCoverage));
        targetCoverage = clamp0to100(round1(targetCoverage));
    }

    public static @NotNull AgentProgress initial(double targetCoverage) {
        long now = System.currentTimeMillis();
        return new AgentProgress(
                AgentUiState.IDLE,
                0,
                0,
                0.0,
                targetCoverage,
                null,
                "Idle",
                now,
                now
        );
    }

    public @NotNull AgentProgress with(
            @NotNull AgentUiState nextState,
            int nextIteration,
            int nextMaxIterations,
            double nextCoverage,
            @Nullable String nextClass,
            @NotNull String message
    ) {
        return new AgentProgress(
                nextState,
                nextIteration,
                nextMaxIterations,
                nextCoverage,
                targetCoverage,
                nextClass,
                message,
                startedAt,
                System.currentTimeMillis()
        );
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double clamp0to100(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
