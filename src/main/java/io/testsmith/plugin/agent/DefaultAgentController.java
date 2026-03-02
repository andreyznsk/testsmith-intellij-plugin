package io.testsmith.plugin.agent;

import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.LlmException;
import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.api.LlmTuning;
import io.testsmith.plugin.llm.api.TestFramework;
import io.testsmith.plugin.settings.ExecutionMode;
import io.testsmith.plugin.ui.model.AgentUiState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class DefaultAgentController implements AgentController, AutoCloseable {
    private static final int MAX_RECENT_EVENTS = 200;
    private static final String PLACEHOLDER_TARGET_CLASS = "com.example.Service";
    private static final String PLACEHOLDER_TARGET_SOURCE = "class Service {}";
    private static final Duration DEFAULT_LLM_TIMEOUT = Duration.ofSeconds(60);

    private final Object lock = new Object();
    private final Supplier<ExecutionMode> modeSupplier;
    private final Supplier<Integer> maxIterationsSupplier;
    private final Supplier<Double> targetCoverageSupplier;
    private final ExecutorService executor;
    private final TestFileWriter testFileWriter;
    private final UnifiedDiffRenderer diffRenderer;
    private final CopyOnWriteArrayList<AgentEventListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ProgressListener> progressListeners = new CopyOnWriteArrayList<>();
    private final Deque<AgentEvent> recentEvents = new ArrayDeque<>();
    private final AtomicReference<AgentState> state = new AtomicReference<>(AgentState.IDLE);
    private final AtomicReference<AgentProgress> progress = new AtomicReference<>(AgentProgress.initial(0.0));
    private final AtomicLong runToken = new AtomicLong(0L);
    private final AtomicReference<UUID> activeRunId = new AtomicReference<>();
    private final AtomicReference<LlmClient> configuredLlmClient = new AtomicReference<>(NoOpLlmClient.INSTANCE);

    private volatile Future<?> runningTask;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile ApprovalGateway approvalGateway = ApprovalGateway.rejecting();
    private volatile PendingApproval pendingApproval;

    DefaultAgentController(
            @NotNull Supplier<ExecutionMode> modeSupplier,
            @NotNull Supplier<Integer> maxIterationsSupplier,
            @NotNull Supplier<Double> targetCoverageSupplier,
            @NotNull ExecutorService executor,
            @NotNull TestFileWriter testFileWriter,
            @NotNull UnifiedDiffRenderer diffRenderer
    ) {
        this.modeSupplier = Objects.requireNonNull(modeSupplier, "modeSupplier");
        this.maxIterationsSupplier = Objects.requireNonNull(maxIterationsSupplier, "maxIterationsSupplier");
        this.targetCoverageSupplier = Objects.requireNonNull(targetCoverageSupplier, "targetCoverageSupplier");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.testFileWriter = Objects.requireNonNull(testFileWriter, "testFileWriter");
        this.diffRenderer = Objects.requireNonNull(diffRenderer, "diffRenderer");
        this.progress.set(AgentProgress.initial(targetCoverageSupplier.get()));
    }

    @Override
    public void start() {
        synchronized (lock) {
            AgentState current = state.get();
            if (current == AgentState.RUNNING || current == AgentState.STOPPING) {
                return;
            }
            if (current == AgentState.WAITING_FOR_APPROVAL) {
                UUID runId = activeRunId.get();
                cancelPendingApprovalLocked(runId, "Cancelled pending approval due to new run");
            }
            stopRequested.set(false);
            state.set(AgentState.RUNNING);
            long token = runToken.incrementAndGet();
            UUID runId = UUID.randomUUID();
            LlmClient runLlmClient = configuredLlmClient.get();
            activeRunId.set(runId);
            publishProgress(AgentUiState.RUNNING, 1, 0.0, null, "[Agent] START", System.currentTimeMillis());
            emit(
                    AgentEventType.RUN_STARTED,
                    "[Agent] START mode=" + modeSupplier.get()
                            + ", runId=" + runId
                            + ", llmClient=" + runLlmClient.getClass().getSimpleName()
            );
            runningTask = executor.submit(() -> runLoop(token, runId, runLlmClient));
        }
    }

    public void start(@NotNull LlmClient llmClient) {
        configuredLlmClient.set(Objects.requireNonNull(llmClient, "llmClient"));
        start();
    }

    public void setLlmClient(@NotNull LlmClient llmClient) {
        configuredLlmClient.set(Objects.requireNonNull(llmClient, "llmClient"));
    }

    @Override
    public void requestStop() {
        synchronized (lock) {
            AgentState current = state.get();
            if (current != AgentState.RUNNING && current != AgentState.WAITING_FOR_APPROVAL && current != AgentState.STOPPING) {
                return;
            }
            if (stopRequested.compareAndSet(false, true)) {
                state.set(AgentState.STOPPING);
                publishProgress(AgentUiState.STOPPING, progress.get().iteration(), progress.get().currentCoverage(), progress.get().currentClass(), "[Agent] STOP_REQUESTED");
                emit(AgentEventType.STOP_REQUESTED, "[Agent] STOP_REQUESTED");
                if (current == AgentState.WAITING_FOR_APPROVAL) {
                    UUID runId = activeRunId.get();
                    cancelPendingApprovalLocked(runId, "Stop requested during approval");
                }
            }
        }
    }

    @Override
    public boolean isRunning() {
        AgentState current = state.get();
        return current == AgentState.RUNNING || current == AgentState.WAITING_FOR_APPROVAL || current == AgentState.STOPPING;
    }

    @Override
    public @NotNull AgentState getState() {
        return state.get();
    }

    @Override
    public @NotNull AgentProgress getProgress() {
        return progress.get();
    }

    @Override
    public void addProgressListener(@NotNull ProgressListener listener) {
        progressListeners.add(Objects.requireNonNull(listener, "listener"));
        listener.onProgressChanged(progress.get());
    }

    @Override
    public void removeProgressListener(@NotNull ProgressListener listener) {
        progressListeners.remove(listener);
    }

    @Override
    public void addListener(@NotNull AgentEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeListener(@NotNull AgentEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public @NotNull List<AgentEvent> getRecentEvents() {
        synchronized (lock) {
            return List.copyOf(recentEvents);
        }
    }

    @Override
    public void close() {
        UUID runId = activeRunId.get();
        if (runId != null) {
            approvalGateway.cancel(runId);
        }
        executor.shutdownNow();
    }

    @Override
    public void setApprovalGateway(@NotNull ApprovalGateway approvalGateway) {
        this.approvalGateway = Objects.requireNonNull(approvalGateway, "approvalGateway");
    }

    private void runLoop(long token, @NotNull UUID runId, @NotNull LlmClient runLlmClient) {
        boolean terminatedByStop = false;
        try {
            emit(AgentEventType.STEP_STARTED, "[Agent] ITERATION 1");
            publishProgress(AgentUiState.ANALYZING, 1, 0.0, "com.example.Service", "Analyzing target");
            runStep("ANALYZE_TARGET", token, 300L);
            publishProgress(AgentUiState.GENERATING, 1, 0.0, "com.example.Service", "Generating test");
            runStep("GENERATE_TEST", token, 350L);
            generateWithLlm(runLlmClient);
            throwIfStopRequested(token, "after GENERATE_TEST");
            GeneratedCandidate candidate = buildCandidate(runId);
            throwIfStopRequested(token, "before approval/write");
            if (modeSupplier.get() == ExecutionMode.MANUAL) {
                publishProgress(AgentUiState.WAITING_APPROVAL, 1, 0.0, candidate.targetClassFqn(), "Waiting for manual approval");
                if (!awaitManualApprovalAndMaybeWrite(token, runId, candidate)) {
                    publishProgress(AgentUiState.STOPPED, 1, 0.0, candidate.targetClassFqn(), "Manual rejection");
                    emit(AgentEventType.RUN_STOPPED, "[Agent] TERMINATED (manual rejection)");
                    return;
                }
            } else {
                throwIfStopRequested(token, "before WRITE_TEST_FILES");
                writeApprovedFiles(candidate.proposedFiles(), candidate.expectedCurrentContent());
            }
            publishProgress(AgentUiState.VERIFYING, 1, 0.0, candidate.targetClassFqn(), "Verifying target");
            runStep("VERIFY_TARGET", token, 1200L);
            if (modeSupplier.get() == ExecutionMode.AUTONOMOUS) {
                runStep("RUN_FULL_SUITE", token, 750L);
                publishProgress(AgentUiState.COVERAGE_UPDATE, 1, 65.0, candidate.targetClassFqn(), "Coverage update");
            }
            if (isStale(token)) {
                return;
            }
            publishProgress(AgentUiState.COMPLETED, 1, progress.get().currentCoverage(), candidate.targetClassFqn(), "[Agent] TERMINATED");
            emit(AgentEventType.RUN_STOPPED, "[Agent] TERMINATED");
        } catch (StopRequestedException ignored) {
            terminatedByStop = true;
        } catch (CancellationException ignored) {
            if (stopRequested.get()) {
                terminatedByStop = true;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            if (stopRequested.get()) {
                terminatedByStop = true;
            } else {
                state.set(AgentState.ERROR);
                publishProgress(AgentUiState.ERROR, progress.get().iteration(), progress.get().currentCoverage(), progress.get().currentClass(), "Run interrupted unexpectedly");
                emit(AgentEventType.RUN_FAILED, "Run interrupted unexpectedly");
            }
        } catch (Exception ex) {
            state.set(AgentState.ERROR);
            publishProgress(AgentUiState.ERROR, progress.get().iteration(), progress.get().currentCoverage(), progress.get().currentClass(), "Run failed: " + ex.getMessage());
            emit(AgentEventType.RUN_FAILED, "Run failed: " + ex.getMessage());
        } finally {
            synchronized (lock) {
                if (Objects.equals(activeRunId.get(), runId)) {
                    cancelPendingApprovalLocked(runId, "Run completed");
                    activeRunId.set(null);
                    if (state.get() != AgentState.ERROR) {
                        state.set(AgentState.IDLE);
                        AgentProgress current = progress.get();
                        AgentUiState terminalState = terminatedByStop ? AgentUiState.STOPPED : current.state();
                        publishProgress(terminalState, current.iteration(), current.currentCoverage(), current.currentClass(), terminatedByStop ? "[Agent] TERMINATED" : current.lastMessage());
                    }
                }
            }
            if (terminatedByStop) {
                emit(AgentEventType.RUN_STOPPED, "[Agent] TERMINATED");
            }
        }
    }

    private void generateWithLlm(@NotNull LlmClient runLlmClient) {
        if (runLlmClient == NoOpLlmClient.INSTANCE) {
            return;
        }
        LlmRequest request = new LlmRequest(
                PLACEHOLDER_TARGET_CLASS,
                PLACEHOLDER_TARGET_SOURCE,
                List.of(),
                TestFramework.JUNIT5,
                "",
                GenerationMode.GENERATE,
                "",
                LlmTuning.defaults(),
                DEFAULT_LLM_TIMEOUT,
                Map.of()
        );
        runLlmClient.generateRaw(request);
    }

    private void runStep(String stepName, long token, long durationMillis) throws InterruptedException {
        if (isStale(token)) {
            throw new InterruptedException("stale run");
        }
        emit(AgentEventType.STEP_STARTED, stepName);
        long remainingMillis = durationMillis;
        while (remainingMillis > 0L) {
            long chunk = Math.min(75L, remainingMillis);
            Thread.sleep(chunk);
            remainingMillis -= chunk;
            if (isStale(token)) {
                throw new InterruptedException("stale run");
            }
            if (stopRequested.get()) {
                break;
            }
        }
        emit(AgentEventType.STEP_FINISHED, stepName);
        if (stopRequested.get() && !isStale(token)) {
            throw new StopRequestedException();
        }
    }

    private boolean isStale(long token) {
        return runToken.get() != token;
    }

    private boolean awaitManualApprovalAndMaybeWrite(long token, UUID runId, GeneratedCandidate candidate) throws Exception {
        if (isStale(token)) {
            return false;
        }
        if (stopRequested.get()) {
            throw new StopRequestedException();
        }
        state.set(AgentState.WAITING_FOR_APPROVAL);
        emit(AgentEventType.STEP_STARTED, "WAITING_FOR_APPROVAL");

        ApprovalRequest request = new ApprovalRequest(
                runId,
                candidate.targetClassFqn(),
                candidate.testClassFqn(),
                candidate.diffText(),
                candidate.proposedFiles(),
                candidate.confidence(),
                candidate.requiresInfrastructure()
        );

        CompletableFuture<ApprovalDecision> decisionFuture = approvalGateway.requestApproval(request);
        pendingApproval = new PendingApproval(
                runId,
                decisionFuture,
                candidate.expectedCurrentContent(),
                candidate.proposedFiles().keySet()
        );
        ApprovalDecision decision;
        try {
            decision = decisionFuture.get();
        } finally {
            pendingApproval = null;
        }
        if (decision == null || decision.type() == DecisionType.REJECT) {
            emit(AgentEventType.STEP_FINISHED, "WAITING_FOR_APPROVAL");
            return false;
        }
        throwIfStopRequested(token, "after manual approval");
        Map<Path, String> approvedFiles = resolveApprovedFiles(request.proposedFiles(), decision.editedFiles());
        state.set(AgentState.RUNNING);
        publishProgress(AgentUiState.RUNNING, 1, progress.get().currentCoverage(), request.targetClassFqn(), "Approval received");
        writeApprovedFiles(approvedFiles, candidate.expectedCurrentContent());
        emit(AgentEventType.STEP_FINISHED, "WAITING_FOR_APPROVAL");
        return true;
    }

    private Map<Path, String> resolveApprovedFiles(
            Map<Path, String> proposedFiles,
            Map<Path, String> editedFiles
    ) {
        if (editedFiles == null || editedFiles.isEmpty()) {
            return proposedFiles;
        }
        if (!proposedFiles.keySet().equals(editedFiles.keySet())) {
            throw new WriteSafetyException("Edited files must match proposed files");
        }
        return editedFiles;
    }

    private void writeApprovedFiles(Map<Path, String> files, Map<Path, String> expectedCurrentContent) throws Exception {
        emit(AgentEventType.STEP_STARTED, "WRITE_TEST_FILES");
        testFileWriter.writeFiles(files, expectedCurrentContent);
        emit(AgentEventType.STEP_FINISHED, "WRITE_TEST_FILES");
    }

    private GeneratedCandidate buildCandidate(UUID runId) throws Exception {
        String targetClassFqn = PLACEHOLDER_TARGET_CLASS;
        String testClassFqn = "com.example.ServiceTest";
        Path targetPath = testFileWriter.resolveTestFile(testClassFqn);
        String newContent = """
                package com.example;

                import org.junit.jupiter.api.Test;

                class ServiceTest {
                    @Test
                    void generatedByTestSmith() {
                    }
                }
                """;
        String oldContent = testFileWriter.readCurrentContent(targetPath);
        String diff = diffRenderer.renderFileDiff(targetPath, oldContent, newContent);
        Map<Path, String> files = Map.of(targetPath, newContent);
        Map<Path, String> expected = new HashMap<>();
        expected.put(targetPath, oldContent);
        return new GeneratedCandidate(runId, targetClassFqn, testClassFqn, files, expected, diff, 0.75, false);
    }

    private void cancelPendingApprovalLocked(@Nullable UUID runId, @NotNull String reason) {
        PendingApproval pending = pendingApproval;
        if (pending == null) {
            return;
        }
        pending.future().cancel(true);
        if (runId != null) {
            approvalGateway.cancel(runId);
        }
        pendingApproval = null;
        emit(AgentEventType.STEP_FINISHED, "WAITING_FOR_APPROVAL (" + reason + ")");
    }

    private void throwIfStopRequested(long token, String phase) throws InterruptedException {
        if (isStale(token)) {
            throw new InterruptedException("stale run");
        }
        if (stopRequested.get()) {
            emit(AgentEventType.STEP_FINISHED, "Cancellation acknowledged at " + phase);
            throw new StopRequestedException();
        }
    }

    private void publishProgress(@NotNull AgentUiState uiState, int iteration, double coverage, @Nullable String currentClass, @NotNull String message) {
        publishProgress(uiState, iteration, coverage, currentClass, message, null);
    }

    private void publishProgress(
            @NotNull AgentUiState uiState,
            int iteration,
            double coverage,
            @Nullable String currentClass,
            @NotNull String message,
            @Nullable Long startedAtOverride
    ) {
        int maxFromSettings = Math.max(0, maxIterationsSupplier.get());
        int normalizedIteration = uiState == AgentUiState.IDLE ? 0 : Math.max(1, iteration);
        int normalizedMaxIterations = uiState == AgentUiState.IDLE ? 0 : Math.max(1, maxFromSettings);
        AgentProgress current = progress.get();
        long startedAt = startedAtOverride == null ? current.startedAt() : startedAtOverride;
        AgentProgress snapshot = new AgentProgress(
                uiState,
                normalizedIteration,
                normalizedMaxIterations,
                coverage,
                targetCoverageSupplier.get(),
                currentClass,
                message,
                startedAt,
                System.currentTimeMillis()
        );
        progress.set(snapshot);
        for (ProgressListener listener : progressListeners) {
            listener.onProgressChanged(snapshot);
        }
    }

    private void emit(AgentEventType type, String message) {
        AgentEvent event = new AgentEvent(type, message, Instant.now());
        synchronized (lock) {
            recentEvents.addLast(event);
            while (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.removeFirst();
            }
        }
        AgentState snapshot = state.get();
        for (AgentEventListener listener : listeners) {
            listener.onAgentUpdated(snapshot, event);
        }
    }

    private static final class StopRequestedException extends RuntimeException {
    }

    private static final class NoOpLlmClient implements LlmClient {
        private static final NoOpLlmClient INSTANCE = new NoOpLlmClient();

        @Override
        public String generateRaw(LlmRequest request) {
            throw new LlmException("No LLM client configured for this run.");
        }
    }

    private record PendingApproval(
            UUID runId,
            CompletableFuture<ApprovalDecision> future,
            Map<Path, String> expectedCurrentContent,
            Set<Path> proposedPaths
    ) {
    }

    private record GeneratedCandidate(
            UUID runId,
            String targetClassFqn,
            String testClassFqn,
            Map<Path, String> proposedFiles,
            Map<Path, String> expectedCurrentContent,
            String diffText,
            Double confidence,
            boolean requiresInfrastructure
    ) {
    }

}
