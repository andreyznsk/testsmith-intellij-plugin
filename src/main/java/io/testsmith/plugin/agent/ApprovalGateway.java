package io.testsmith.plugin.agent;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ApprovalGateway {
    @NotNull CompletableFuture<ApprovalDecision> requestApproval(@NotNull ApprovalRequest request);

    void cancel(@NotNull UUID runId);

    static @NotNull ApprovalGateway rejecting() {
        return new ApprovalGateway() {
            @Override
            public @NotNull CompletableFuture<ApprovalDecision> requestApproval(@NotNull ApprovalRequest request) {
                return CompletableFuture.completedFuture(ApprovalDecision.reject());
            }

            @Override
            public void cancel(@NotNull UUID runId) {
                // no-op
            }
        };
    }
}
