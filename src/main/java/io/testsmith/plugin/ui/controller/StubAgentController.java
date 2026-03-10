package io.testsmith.plugin.ui.controller;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import io.testsmith.plugin.ui.model.AgentUiModel;
import io.testsmith.plugin.ui.model.AgentUiState;
import org.jetbrains.annotations.NotNull;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class StubAgentController implements AgentController, Disposable {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Project project;
    private final AgentUiModel model;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong runId = new AtomicLong(0L);

    public StubAgentController(@NotNull Project project, @NotNull AgentUiModel model) {
        this.project = Objects.requireNonNull(project, "project");
        this.model = Objects.requireNonNull(model, "model");
        this.model.setCoverageSummary("Coverage: 0/0 lines (stub)");
        log("Stub controller ready for project: " + project.getName());
    }

    @Override
    public void start() {
        AgentUiState current = model.getState();
        if (current != AgentUiState.IDLE && current != AgentUiState.ERROR) {
            log("Run ignored: current state is " + current);
            return;
        }
        long myRunId = runId.incrementAndGet();

        transitionTo(AgentUiState.RUNNING, "[Agent] START (stub)");
        scheduler.schedule(() -> {
            if (isStaleRun(myRunId)) {
                return;
            }
            model.setState(AgentUiState.IDLE);
            log("[Agent] TERMINATED (stub)");
        }, 1200L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        runId.incrementAndGet();
        model.setState(AgentUiState.STOPPING);
        model.setProposalText(null);
        scheduler.schedule(() -> {
            model.setState(AgentUiState.IDLE);
            log("[Agent] TERMINATED (stub)");
        }, 120L, TimeUnit.MILLISECONDS);
        log("[Agent] STOP_REQUESTED (stub)");
    }

    @Override
    public void analyzeCoverage() {
        log("Coverage analyzed (stub)");
    }

    @Override
    public void approve() {
        log("Approve ignored in simplified stub state model");
    }

    @Override
    public void reject() {
        log("Reject ignored in simplified stub state model");
    }

    @Override
    public void openSettings() {
        log("Open settings requested");
    }

    @Override
    public void editProposal() {
        log("Edit ignored in simplified stub state model");
    }

    @Override
    public void dispose() {
        scheduler.shutdownNow();
    }

    private void transitionTo(AgentUiState state, String message) {
        model.setState(state);
        log(message);
    }

    private void log(String message) {
        model.appendLog("[" + LocalTime.now().format(LOG_TIME) + "] " + message);
    }

    private boolean isStaleRun(long myRunId) {
        return runId.get() != myRunId;
    }
}
