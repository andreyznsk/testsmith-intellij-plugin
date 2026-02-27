package io.testsmith.plugin.agent;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface AgentEventListener {
    void onAgentUpdated(@NotNull AgentState state, @NotNull AgentEvent event);
}
