package io.testsmith.plugin.ui.model;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultAgentUiModel implements AgentUiModel {
    private static final int MAX_LOG_ENTRIES = 500;

    private final Object lock = new Object();
    private final List<String> logEntries = new ArrayList<>();
    private final CopyOnWriteArrayList<AgentEventListener> listeners = new CopyOnWriteArrayList<>();

    private AgentUiState state = AgentUiState.IDLE;
    private String proposalText;
    private String coverageSummary = "Coverage: --";

    @Override
    public @NotNull AgentUiState getState() {
        synchronized (lock) {
            return state;
        }
    }

    @Override
    public @Nullable String getProposalText() {
        synchronized (lock) {
            return proposalText;
        }
    }

    @Override
    public @NotNull String getCoverageSummary() {
        synchronized (lock) {
            return coverageSummary;
        }
    }

    @Override
    public @NotNull List<String> getLogEntries() {
        synchronized (lock) {
            return List.copyOf(logEntries);
        }
    }

    @Override
    public void setState(@NotNull AgentUiState state) {
        Objects.requireNonNull(state, "state");
        synchronized (lock) {
            this.state = state;
        }
        notifyListeners();
    }

    @Override
    public void setProposalText(@Nullable String proposalText) {
        synchronized (lock) {
            this.proposalText = proposalText;
        }
        notifyListeners();
    }

    @Override
    public void setCoverageSummary(@NotNull String coverageSummary) {
        Objects.requireNonNull(coverageSummary, "coverageSummary");
        synchronized (lock) {
            this.coverageSummary = coverageSummary;
        }
        notifyListeners();
    }

    @Override
    public void appendLog(@NotNull String entry) {
        Objects.requireNonNull(entry, "entry");
        synchronized (lock) {
            logEntries.add(entry);
            if (logEntries.size() > MAX_LOG_ENTRIES) {
                logEntries.removeFirst();
            }
        }
        notifyListeners();
    }

    @Override
    public void addListener(@NotNull AgentEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeListener(@NotNull AgentEventListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (AgentEventListener listener : listeners) {
                listener.onModelChanged();
            }
        });
    }
}
