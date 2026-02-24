package io.testsmith.plugin.ui.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface AgentUiModel {
    @NotNull AgentUiState getState();

    @Nullable String getProposalText();

    @NotNull String getCoverageSummary();

    @NotNull List<String> getLogEntries();

    void setState(@NotNull AgentUiState state);

    void setProposalText(@Nullable String proposalText);

    void setCoverageSummary(@NotNull String coverageSummary);

    void appendLog(@NotNull String entry);

    void addListener(@NotNull AgentEventListener listener);

    void removeListener(@NotNull AgentEventListener listener);
}
