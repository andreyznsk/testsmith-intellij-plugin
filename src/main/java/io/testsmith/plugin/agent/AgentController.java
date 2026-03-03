package io.testsmith.plugin.agent;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface AgentController extends AgentSession {
    @NotNull AgentState getState();

    @NotNull AgentProgress getProgress();

    void addProgressListener(@NotNull ProgressListener listener);

    void removeProgressListener(@NotNull ProgressListener listener);

    void addListener(@NotNull AgentEventListener listener);

    void removeListener(@NotNull AgentEventListener listener);

    @NotNull List<AgentEvent> getRecentEvents();

    void setApprovalGateway(@NotNull ApprovalGateway approvalGateway);
}
