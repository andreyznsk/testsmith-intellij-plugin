package io.testsmith.plugin.agent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import io.testsmith.plugin.llm.LlmClientFactory;
import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.LlmMisconfigurationException;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import io.testsmith.plugin.settings.TestSmithProjectSettingsService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

@Service(Service.Level.PROJECT)
public final class AgentControllerService implements AgentController, Disposable {
    private static final Logger LOG = Logger.getInstance(AgentControllerService.class);

    private final Project project;
    private final LlmClientFactory llmClientFactory;
    private final CoverageAnalyzeService coverageAnalyzeService;
    private final DefaultAgentController delegate;

    public AgentControllerService(@NotNull Project project) {
        this.project = project;
        this.llmClientFactory = new LlmClientFactory();
        this.coverageAnalyzeService = new CoverageAnalyzeService();
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
        TestSmithProjectSettings settings = TestSmithProjectSettingsService.getInstance(project).getSettings();
        LlmClient llmClient;
        try {
            llmClient = llmClientFactory.create(project, settings);
        } catch (LlmMisconfigurationException ex) {
            LOG.warn("Agent run aborted due to LLM misconfiguration: " + ex.getMessage());
            return;
        }
        delegate.start(llmClient);
    }

    @Override
    public void requestStop() {
        delegate.requestStop();
    }

    @Override
    public void analyzeCoverage() {
        LOG.info("Coverage analyze requested");
        if (delegate.getState() != AgentState.IDLE) {
            LOG.info("Coverage analyze ignored: agent state is " + delegate.getState());
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "TestSmith: Analyze Coverage", false) {
            private CoverageAnalyzeService.CoverageAnalysisResult result;
            private CoverageAnalyzeService.CoverageAnalyzeException failure;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Reading JaCoCo XML report");
                try {
                    TestSmithProjectSettings settings = TestSmithProjectSettingsService.getInstance(project).getSettings();
                    result = coverageAnalyzeService.analyze(project.getBasePath(), settings);
                } catch (CoverageAnalyzeService.CoverageAnalyzeException ex) {
                    failure = ex;
                }
            }

            @Override
            public void onSuccess() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (project.isDisposed()) {
                        return;
                    }
                    if (failure != null) {
                        LOG.warn("Coverage analyze failed: " + failure.getMessage(), failure);
                        Messages.showErrorDialog(project, failure.getMessage(), "TestSmith Coverage Analysis");
                        return;
                    }
                    if (result == null) {
                        return;
                    }
                    LOG.info("Coverage analyzed: " + String.format("%.1f", result.coveragePercent()));
                    String message = String.format(
                            "Coverage analyzed: %.1f%% (%d/%d lines)",
                            result.coveragePercent(),
                            result.coveredLines(),
                            result.coveredLines() + result.missedLines()
                    );
                    boolean applied = delegate.applyCoverageAnalysis(result.coveragePercent(), message);
                    if (!applied) {
                        LOG.info("Coverage analysis result ignored: agent state is " + delegate.getState());
                    }
                });
            }
        });
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
