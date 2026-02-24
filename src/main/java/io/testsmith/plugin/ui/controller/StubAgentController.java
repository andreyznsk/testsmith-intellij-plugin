package io.testsmith.plugin.ui.controller;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import io.testsmith.plugin.ui.model.AgentUiModel;
import io.testsmith.plugin.ui.model.AgentUiState;
import org.jetbrains.annotations.NotNull;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
        if (current != AgentUiState.IDLE && current != AgentUiState.STOPPED && current != AgentUiState.ERROR) {
            log("Run ignored: current state is " + current);
            return;
        }
        long myRunId = runId.incrementAndGet();

        log("Run requested");
        if (isCurrentRun(myRunId)) {
            return;
        }
        transitionTo(AgentUiState.ANALYZING, "Analyzing target class");

        List<Step> steps = new ArrayList<>();
        steps.add(new Step(AgentUiState.GENERATING, "Generating candidate test"));
        steps.add(new Step(AgentUiState.VERIFYING_TARGET, "Verifying target class tests"));
        steps.add(new Step(AgentUiState.RUNNING_FULL_SUITE, "Running full suite (stub)"));
        steps.add(new Step(AgentUiState.WAITING_FOR_APPROVAL, "Waiting for approval"));

        long delayMillis = 600L;
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            scheduler.schedule(() -> {
                if (isCurrentRun(myRunId)) {
                    return;
                }
                transitionTo(step.state(), step.logMessage());
                if (step.state() == AgentUiState.WAITING_FOR_APPROVAL) {
                    model.setProposalText("// Proposed test diff (stub)\\n+ @Test\\n+ void shouldDoSomething() {\\n+     // TODO: generated test\\n+ }");
                }
            }, (i + 1) * delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        runId.incrementAndGet();
        model.setState(AgentUiState.STOPPED);
        model.setProposalText(null);
        log("Stopped by user");
    }

    @Override
    public void approve() {
        if (model.getState() != AgentUiState.WAITING_FOR_APPROVAL) {
            log("Approve ignored: no proposal pending");
            return;
        }
        log("Proposal approved");
        runId.incrementAndGet();
        model.setProposalText(null);
        model.setState(AgentUiState.IDLE);
    }

    @Override
    public void reject() {
        if (model.getState() != AgentUiState.WAITING_FOR_APPROVAL) {
            log("Reject ignored: no proposal pending");
            return;
        }
        log("Proposal rejected");
        runId.incrementAndGet();
        model.setProposalText(null);
        model.setState(AgentUiState.STOPPED);
    }

    @Override
    public void openSettings() {
        log("Open settings requested");
    }

    @Override
    public void editProposal() {
        if (model.getState() != AgentUiState.WAITING_FOR_APPROVAL) {
            log("Edit ignored: no proposal pending");
            return;
        }
        log("Edit proposal requested (stub)");
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

    private boolean isCurrentRun(long myRunId) {
        return runId.get() != myRunId;
    }

    private record Step(AgentUiState state, String logMessage) {
    }
}
