package io.testsmith.plugin.agent;

import io.testsmith.plugin.settings.ExecutionMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class DefaultAgentController implements AgentController, AutoCloseable {
    private static final int MAX_RECENT_EVENTS = 200;

    private final Object lock = new Object();
    private final Supplier<ExecutionMode> modeSupplier;
    private final ExecutorService executor;
    private final TestFileWriter testFileWriter;
    private final UnifiedDiffRenderer diffRenderer;
    private final CopyOnWriteArrayList<AgentEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Deque<AgentEvent> recentEvents = new ArrayDeque<>();
    private final AtomicReference<AgentState> state = new AtomicReference<>(AgentState.IDLE);
    private final AtomicLong runToken = new AtomicLong(0L);
    private final AtomicReference<UUID> activeRunId = new AtomicReference<>();

    private volatile Future<?> runningTask;
    private volatile boolean stopRequested;
    private volatile ApprovalGateway approvalGateway = ApprovalGateway.rejecting();
    private volatile PendingApproval pendingApproval;

    public DefaultAgentController(@NotNull Supplier<ExecutionMode> modeSupplier) {
        this(
                modeSupplier,
                Executors.newSingleThreadExecutor(newThreadFactory()),
                new LocalFsTestFileWriter(),
                new UnifiedDiffRenderer()
        );
    }

    DefaultAgentController(
            @NotNull Supplier<ExecutionMode> modeSupplier,
            @NotNull ExecutorService executor,
            @NotNull TestFileWriter testFileWriter,
            @NotNull UnifiedDiffRenderer diffRenderer
    ) {
        this.modeSupplier = Objects.requireNonNull(modeSupplier, "modeSupplier");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.testFileWriter = Objects.requireNonNull(testFileWriter, "testFileWriter");
        this.diffRenderer = Objects.requireNonNull(diffRenderer, "diffRenderer");
    }

    @Override
    public void start() {
        synchronized (lock) {
            AgentState current = state.get();
            if (current == AgentState.RUNNING || current == AgentState.STOP_REQUESTED) {
                return;
            }
            if (current == AgentState.WAITING_FOR_APPROVAL) {
                UUID runId = activeRunId.get();
                cancelPendingApprovalLocked(runId, "Cancelled pending approval due to new run");
            }
            stopRequested = false;
            state.set(AgentState.RUNNING);
            long token = runToken.incrementAndGet();
            UUID runId = UUID.randomUUID();
            activeRunId.set(runId);
            emit(AgentEventType.RUN_STARTED, "mode=" + modeSupplier.get() + ", runToken=" + token + ", runId=" + runId);
            runningTask = executor.submit(() -> runLoop(token, runId));
        }
    }

    @Override
    public void requestStop() {
        synchronized (lock) {
            AgentState current = state.get();
            if (current != AgentState.RUNNING && current != AgentState.WAITING_FOR_APPROVAL) {
                return;
            }
            stopRequested = true;
            state.set(AgentState.STOP_REQUESTED);
            emit(AgentEventType.STOP_REQUESTED, "Stop requested by user");
            if (current == AgentState.WAITING_FOR_APPROVAL) {
                UUID runId = activeRunId.get();
                cancelPendingApprovalLocked(runId, "Stop requested during approval");
                runToken.incrementAndGet();
                state.set(AgentState.STOPPED);
                emit(AgentEventType.RUN_STOPPED, "Stopped during approval");
            }
        }
    }

    @Override
    public @NotNull AgentState getState() {
        return state.get();
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

    private void runLoop(long token, @NotNull UUID runId) {
        try {
            runStep("ANALYZE_TARGET", token, 300L);
            runStep("GENERATE_TEST", token, 350L);
            GeneratedCandidate candidate = buildCandidate(runId);
            if (modeSupplier.get() == ExecutionMode.MANUAL) {
                if (!awaitManualApprovalAndMaybeWrite(token, runId, candidate)) {
                    state.set(AgentState.STOPPED);
                    emit(AgentEventType.RUN_STOPPED, "User rejected test");
                    return;
                }
            } else {
                writeApprovedFiles(candidate.proposedFiles(), candidate.expectedCurrentContent());
            }
            runStep("VERIFY_TARGET", token, 1200L);
            if (modeSupplier.get() == ExecutionMode.AUTONOMOUS) {
                runStep("RUN_FULL_SUITE", token, 750L);
            }
            if (isStale(token)) {
                return;
            }
            state.set(AgentState.STOPPED);
            emit(AgentEventType.RUN_STOPPED, "Run finished");
        } catch (StopRequestedException ignored) {
            // Expected terminal condition after the current phase completes.
        } catch (CancellationException ignored) {
            if (stopRequested) {
                state.set(AgentState.STOPPED);
                emit(AgentEventType.RUN_STOPPED, "Run cancelled");
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            state.set(AgentState.STOPPED);
            emit(AgentEventType.RUN_STOPPED, "Run interrupted and stopped");
        } catch (Exception ex) {
            state.set(AgentState.FAILED);
            emit(AgentEventType.RUN_FAILED, "Run failed: " + ex.getMessage());
        } finally {
            synchronized (lock) {
                if (Objects.equals(activeRunId.get(), runId)) {
                    cancelPendingApprovalLocked(runId, "Run completed");
                    activeRunId.set(null);
                }
            }
        }
    }

    private void runStep(String stepName, long token, long durationMillis) throws InterruptedException {
        if (isStale(token)) {
            throw new InterruptedException("stale run");
        }
        emit(AgentEventType.STEP_STARTED, stepName);
        Thread.sleep(durationMillis);
        emit(AgentEventType.STEP_FINISHED, stepName);
        if (stopRequested && !isStale(token)) {
            state.set(AgentState.STOPPED);
            emit(AgentEventType.RUN_STOPPED, "Stopped after " + stepName);
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
        if (isStale(token)) {
            throw new InterruptedException("stale run");
        }
        Map<Path, String> approvedFiles = resolveApprovedFiles(request.proposedFiles(), decision.editedFiles());
        state.set(AgentState.RUNNING);
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
        String targetClassFqn = "com.example.Service";
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

    private static ThreadFactory newThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "testsmith-agent-runner");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class StopRequestedException extends RuntimeException {
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

    private static final class LocalFsTestFileWriter implements TestFileWriter {
        @Override
        public @NotNull Path resolveTestFile(@NotNull String testClassFqn) {
            String relative = testClassFqn.replace('.', '/') + ".java";
            return Path.of(System.getProperty("user.dir"))
                    .resolve("src")
                    .resolve("test")
                    .resolve("java")
                    .resolve(relative)
                    .toAbsolutePath()
                    .normalize();
        }

        @Override
        public @Nullable String readCurrentContent(@NotNull Path path) throws Exception {
            Path normalized = path.toAbsolutePath().normalize();
            return java.nio.file.Files.exists(normalized)
                    ? java.nio.file.Files.readString(normalized)
                    : null;
        }

        @Override
        public void writeFiles(@NotNull Map<Path, String> files, @NotNull Map<Path, String> expectedCurrentContent) throws Exception {
            for (Map.Entry<Path, String> entry : files.entrySet()) {
                Path path = entry.getKey().toAbsolutePath().normalize();
                String expected = expectedCurrentContent.get(path);
                String actual = readCurrentContent(path);
                if (!Objects.equals(expected, actual)) {
                    throw new WriteSafetyException("File changed on disk before apply: " + path);
                }
            }
            for (Map.Entry<Path, String> entry : files.entrySet()) {
                Path path = entry.getKey().toAbsolutePath().normalize();
                java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.writeString(path, entry.getValue());
            }
        }
    }
}
