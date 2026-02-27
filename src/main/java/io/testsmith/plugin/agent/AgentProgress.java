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
        if (iteration < 0) {
            throw new IllegalArgumentException("iteration must be >= 0");
        }
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations must be >= 0");
        }
        currentCoverage = round1(currentCoverage);
        targetCoverage = round1(targetCoverage);
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
}
