package io.testsmith.plugin.agent;

import io.testsmith.plugin.settings.ExecutionMode;
import io.testsmith.plugin.ui.model.AgentUiState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAgentControllerTest {
    @Test
    void startIsIdempotentAndRunStops() throws Exception {
        StubTestFileWriter writer = new StubTestFileWriter();
        DefaultAgentController controller = newController(() -> ExecutionMode.MANUAL, writer);
        controller.setApprovalGateway(new ImmediateApprovalGateway(ApprovalDecision.reject()));
        try {
            controller.start();
            controller.start();

            waitForState(controller, AgentState.IDLE, Duration.ofSeconds(5));

            long starts = controller.getRecentEvents().stream()
                    .filter(event -> event.type() == AgentEventType.RUN_STARTED)
                    .count();
            assertEquals(1L, starts);
            assertEquals(0, writer.writeCount.get(), "Reject should prevent writes");
        } finally {
            controller.close();
        }
    }

    @Test
    void requestStopTransitionsToStopRequestedThenStoppedAfterCurrentStep() throws Exception {
        StubTestFileWriter writer = new StubTestFileWriter();
        DefaultAgentController controller = newController(() -> ExecutionMode.MANUAL, writer);
        controller.setApprovalGateway(new ImmediateApprovalGateway(ApprovalDecision.approve(null)));
        CountDownLatch verifyStarted = new CountDownLatch(1);

        controller.addListener((state, event) -> {
            if (event.type() == AgentEventType.STEP_STARTED && "VERIFY_TARGET".equals(event.message())) {
                verifyStarted.countDown();
            }
        });

        try {
            controller.start();
            assertTrue(verifyStarted.await(5, TimeUnit.SECONDS), "VERIFY_TARGET step did not start in time");

            controller.requestStop();
            assertEquals(AgentState.STOPPING, controller.getState());
            waitForState(controller, AgentState.IDLE, Duration.ofSeconds(5));

            List<AgentEvent> events = controller.getRecentEvents();
            int stopRequested = indexOf(events, AgentEventType.STOP_REQUESTED, "[Agent] STOP_REQUESTED");
            int verifyFinished = indexOf(events, AgentEventType.STEP_FINISHED, "VERIFY_TARGET");
            int runStopped = indexOf(events, AgentEventType.RUN_STOPPED, "[Agent] TERMINATED");

            assertTrue(stopRequested >= 0, "STOP_REQUESTED event missing");
            assertTrue(verifyFinished > stopRequested, "VERIFY_TARGET should finish after stop is requested");
            assertTrue(runStopped > verifyFinished, "Run should stop after current step finishes");
        } finally {
            controller.close();
        }
    }

    @Test
    void stopDuringWaitingStepIsResponsive() throws Exception {
        StubTestFileWriter writer = new StubTestFileWriter();
        DefaultAgentController controller = newController(() -> ExecutionMode.MANUAL, writer);
        controller.setApprovalGateway(new ImmediateApprovalGateway(ApprovalDecision.approve(null)));

        try {
            controller.start();
            long started = System.nanoTime();
            controller.requestStop();
            waitForState(controller, AgentState.IDLE, Duration.ofSeconds(5));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMillis < 250L, "Stop should complete quickly for waiting-only steps, elapsed=" + elapsedMillis + "ms");
        } finally {
            controller.close();
        }
    }

    @Test
    void manualModeDoesNotWriteWithoutApprove() throws Exception {
        StubTestFileWriter writer = new StubTestFileWriter();
        DefaultAgentController controller = newController(() -> ExecutionMode.MANUAL, writer);
        controller.setApprovalGateway(new ImmediateApprovalGateway(ApprovalDecision.reject()));

        try {
            controller.start();
            waitForState(controller, AgentState.IDLE, Duration.ofSeconds(5));
            assertEquals(0, writer.writeCount.get());
        } finally {
            controller.close();
        }
    }

    @Test
    void manualModeWritesAfterApprove() throws Exception {
        StubTestFileWriter writer = new StubTestFileWriter();
        DefaultAgentController controller = newController(() -> ExecutionMode.MANUAL, writer);
        controller.setApprovalGateway(new ImmediateApprovalGateway(ApprovalDecision.approve(null)));

        try {
            controller.start();
            waitForState(controller, AgentState.IDLE, Duration.ofSeconds(5));
            assertEquals(1, writer.writeCount.get());
            assertFalse(writer.lastWrite.isEmpty());
        } finally {
            controller.close();
        }
    }

    @Test
    void stopDuringApprovalCancelsPendingFutureAndDoesNotWrite() throws Exception {
        StubTestFileWriter writer = new StubTestFileWriter();
        ControlledApprovalGateway gateway = new ControlledApprovalGateway();
        DefaultAgentController controller = newController(() -> ExecutionMode.MANUAL, writer);
        controller.setApprovalGateway(gateway);

        try {
            controller.start();
            ApprovalRequest request = gateway.awaitRequest(Duration.ofSeconds(5));
            assertNotNull(request);

            controller.requestStop();
            waitForState(controller, AgentState.IDLE, Duration.ofSeconds(5));

            assertEquals(1, gateway.cancelCount.get(), "Pending approval should be cancelled");
            assertEquals(0, writer.writeCount.get(), "No writes are allowed after stop");
        } finally {
            controller.close();
        }
    }

    @Test
    void newRunInvalidatesPreviousApprovalAndPreventsStaleWrite() throws Exception {
        StubTestFileWriter writer = new StubTestFileWriter();
        ControlledApprovalGateway gateway = new ControlledApprovalGateway();
        DefaultAgentController controller = newController(() -> ExecutionMode.MANUAL, writer);
        controller.setApprovalGateway(gateway);

        try {
            controller.start();
            ApprovalRequest first = gateway.awaitRequest(Duration.ofSeconds(5));
            controller.start();
            ApprovalRequest second = gateway.awaitRequest(Duration.ofSeconds(5));

            gateway.complete(first.runId(), ApprovalDecision.approve(null));
            gateway.complete(second.runId(), ApprovalDecision.approve(null));

            waitForState(controller, AgentState.IDLE, Duration.ofSeconds(5));
            assertEquals(1, writer.writeCount.get(), "Only latest run may write files");
        } finally {
            controller.close();
        }
    }

    @Test
    void progressShowsWaitingApprovalThenStoppedOnManualReject() throws Exception {
        StubTestFileWriter writer = new StubTestFileWriter();
        ControlledApprovalGateway gateway = new ControlledApprovalGateway();
        DefaultAgentController controller = newController(() -> ExecutionMode.MANUAL, writer);
        controller.setApprovalGateway(gateway);

        try {
            controller.start();
            ApprovalRequest request = gateway.awaitRequest(Duration.ofSeconds(5));
            assertNotNull(request);
            assertEquals(AgentUiState.WAITING_APPROVAL, controller.getProgress().state());

            gateway.complete(request.runId(), ApprovalDecision.reject());
            waitForState(controller, AgentState.IDLE, Duration.ofSeconds(5));
            assertEquals(AgentUiState.STOPPED, controller.getProgress().state());
        } finally {
            controller.close();
        }
    }

    @Test
    void autonomousRunPublishesCoverageUpdateAndCompletedState() throws Exception {
        StubTestFileWriter writer = new StubTestFileWriter();
        DefaultAgentController controller = newController(() -> ExecutionMode.AUTONOMOUS, writer);
        controller.setApprovalGateway(new ImmediateApprovalGateway(ApprovalDecision.approve(null)));

        try {
            controller.start();
            waitForState(controller, AgentState.IDLE, Duration.ofSeconds(5));
            AgentProgress progress = controller.getProgress();
            assertEquals(AgentUiState.COMPLETED, progress.state());
            assertEquals(65.0, progress.currentCoverage());
        } finally {
            controller.close();
        }
    }

    private static DefaultAgentController newController(
            java.util.function.Supplier<ExecutionMode> modeSupplier,
            StubTestFileWriter writer
    ) {
        return new DefaultAgentController(
                modeSupplier,
                () -> 20,
                () -> 80.0,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                writer,
                new UnifiedDiffRenderer()
        );
    }

    private static void waitForState(DefaultAgentController controller, AgentState expected, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (controller.getState() == expected) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("Expected state " + expected + " but was " + controller.getState());
    }

    private static int indexOf(List<AgentEvent> events, AgentEventType type, String message) {
        for (int i = 0; i < events.size(); i++) {
            AgentEvent event = events.get(i);
            if (event.type() == type && message.equals(event.message())) {
                return i;
            }
        }
        return -1;
    }

    private static final class ControlledApprovalGateway implements ApprovalGateway {
        private final Object lock = new Object();
        private final Map<UUID, CompletableFuture<ApprovalDecision>> futures = new LinkedHashMap<>();
        private final List<ApprovalRequest> requests = new ArrayList<>();
        private final AtomicInteger cancelCount = new AtomicInteger();

        @Override
        public CompletableFuture<ApprovalDecision> requestApproval(ApprovalRequest request) {
            synchronized (lock) {
                CompletableFuture<ApprovalDecision> future = new CompletableFuture<>();
                requests.add(request);
                futures.put(request.runId(), future);
                lock.notifyAll();
                return future;
            }
        }

        @Override
        public void cancel(UUID runId) {
            cancelCount.incrementAndGet();
            synchronized (lock) {
                CompletableFuture<ApprovalDecision> future = futures.remove(runId);
                if (future != null) {
                    future.cancel(true);
                }
            }
        }

        ApprovalRequest awaitRequest(Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            synchronized (lock) {
                while (requests.isEmpty() && System.nanoTime() < deadline) {
                    long remainingMs = Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L);
                    lock.wait(remainingMs);
                }
                if (requests.isEmpty()) {
                    throw new AssertionError("No approval request received");
                }
                return requests.remove(0);
            }
        }

        void complete(UUID runId, ApprovalDecision decision) {
            synchronized (lock) {
                CompletableFuture<ApprovalDecision> future = futures.remove(runId);
                if (future != null) {
                    future.complete(decision);
                }
            }
        }
    }

    private static final class ImmediateApprovalGateway implements ApprovalGateway {
        private final ApprovalDecision decision;

        private ImmediateApprovalGateway(ApprovalDecision decision) {
            this.decision = decision;
        }

        @Override
        public CompletableFuture<ApprovalDecision> requestApproval(ApprovalRequest request) {
            return CompletableFuture.completedFuture(decision);
        }

        @Override
        public void cancel(UUID runId) {
            // no-op
        }
    }

    private static final class StubTestFileWriter implements TestFileWriter {
        private final Path file = Path.of("/tmp/testsmith/ServiceTest.java");
        private final AtomicInteger writeCount = new AtomicInteger();
        private final Map<Path, String> lastWrite = new LinkedHashMap<>();

        @Override
        public Path resolveTestFile(String testClassFqn) {
            return file;
        }

        @Override
        public String readCurrentContent(Path path) {
            return null;
        }

        @Override
        public void writeFiles(Map<Path, String> files, Map<Path, String> expectedCurrentContent) {
            writeCount.incrementAndGet();
            lastWrite.clear();
            lastWrite.putAll(files);
        }
    }
}
