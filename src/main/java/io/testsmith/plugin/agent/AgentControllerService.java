package io.testsmith.plugin.agent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import io.testsmith.plugin.settings.TestSmithProjectSettingsService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

@Service(Service.Level.PROJECT)
public final class AgentControllerService implements AgentController, Disposable {
    private final DefaultAgentController delegate;

    public AgentControllerService(@NotNull Project project) {
        this.delegate = new DefaultAgentController(
                () -> TestSmithProjectSettingsService.getInstance(project).getSettings().executionMode,
                () -> TestSmithProjectSettingsService.getInstance(project).getSettings().maxIterations,
                () -> (double) TestSmithProjectSettingsService.getInstance(project).getSettings().targetCoverage,
                java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "testsmith-agent-runner");
                    thread.setDaemon(true);
                    return thread;
                }),
                new SafeTestFileWriter(project),
                new UnifiedDiffRenderer()
        );
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void requestStop() {
        delegate.requestStop();
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    @Override
    public @NotNull AgentState getState() {
        return delegate.getState();
    }

    @Override
    public @NotNull AgentProgress getProgress() {
        return delegate.getProgress();
    }

    @Override
    public void addProgressListener(@NotNull ProgressListener listener) {
        delegate.addProgressListener(listener);
    }

    @Override
    public void removeProgressListener(@NotNull ProgressListener listener) {
        delegate.removeProgressListener(listener);
    }

    @Override
    public void addListener(@NotNull AgentEventListener listener) {
        delegate.addListener(listener);
    }

    @Override
    public void removeListener(@NotNull AgentEventListener listener) {
        delegate.removeListener(listener);
    }

    @Override
    public @NotNull List<AgentEvent> getRecentEvents() {
        return delegate.getRecentEvents();
    }

    @Override
    public void setApprovalGateway(@NotNull ApprovalGateway approvalGateway) {
        delegate.setApprovalGateway(Objects.requireNonNull(approvalGateway, "approvalGateway"));
    }

    @Override
    public void dispose() {
        delegate.close();
    }
}
