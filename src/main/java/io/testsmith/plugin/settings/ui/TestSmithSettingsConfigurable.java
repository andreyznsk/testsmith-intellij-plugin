package io.testsmith.plugin.settings.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.testsmith.plugin.llm.LlmProviderHealthCheckService;
import io.testsmith.plugin.llm.api.HealthCheckResult;
import io.testsmith.plugin.llm.security.SecretSanitizer;
import io.testsmith.plugin.settings.LlmProvider;
import io.testsmith.plugin.settings.ExecutionMode;
import io.testsmith.plugin.settings.TestSmithProjectSettings;
import io.testsmith.plugin.settings.TestSmithProjectSettingsService;
import io.testsmith.plugin.settings.TestSmithSecretsStore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public final class TestSmithSettingsConfigurable implements SearchableConfigurable {
    private static final Logger LOG = Logger.getInstance(TestSmithSettingsConfigurable.class);

    private final Project project;
    private final TestSmithSecretsStore secretsStore = new TestSmithSecretsStore();
    private final LlmProviderHealthCheckService healthCheckService = new LlmProviderHealthCheckService();
    private TestSmithSettingsPanel panel;

    public TestSmithSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String getId() {
        return "io.testsmith.plugin.settings";
    }

    @Override
    public @Nls String getDisplayName() {
        return "TestSmith";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new TestSmithSettingsPanel(project);
        panel.setTestConnectionAction(this::runConnectionTest);
        reset();
        return panel.getComponent();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return panel == null ? null : panel.getPreferredFocusedComponent();
    }

    @Override
    public boolean isModified() {
        if (panel == null) {
            return false;
        }
        TestSmithProjectSettings settings = TestSmithProjectSettingsService.getInstance(project).getSettings();
        return panel.isModified(settings);
    }

    @Override
    public void apply() throws ConfigurationException {
        if (panel == null) {
            return;
        }
        TestSmithProjectSettingsService service = TestSmithProjectSettingsService.getInstance(project);
        ExecutionMode beforeMode = service.getSettings().executionMode;

        com.intellij.openapi.ui.ValidationInfo validation = panel.validateForApply();
        if (validation != null) {
            if (validation.component != null) {
                validation.component.requestFocusInWindow();
            }
            // 2nd arg is title in this IntelliJ SDK
            throw new ConfigurationException(validation.message, "TestSmith Settings");
        }

        ExecutionMode newMode = panel.getExecutionMode();
        if (newMode == ExecutionMode.AUTONOMOUS && beforeMode != ExecutionMode.AUTONOMOUS) {
            int result = Messages.showYesNoDialog(
                    project,
                    "Enable Autonomous mode for this project?",
                    "Confirm Autonomous Mode",
                    Messages.getWarningIcon()
            );
            if (result != Messages.YES) {
                throw new ConfigurationException("Autonomous mode requires confirmation.");
            }
        }

        TestSmithProjectSettings updated = new TestSmithProjectSettings();
        panel.applyTo(updated);
        service.loadState(updated);

        String savedOpenAiKey = panel.getOpenAiKey();
        String savedGigaChatKey = panel.getGigaChatKey();
        secretsStore.setOpenAiKey(project, savedOpenAiKey);
        secretsStore.setGigaChatKey(project, savedGigaChatKey);

        panel.markClean(savedOpenAiKey, savedGigaChatKey);
    }

    @Override
    public void reset() {
        if (panel == null) {
            return;
        }
        TestSmithProjectSettings settings = TestSmithProjectSettingsService.getInstance(project).getSettings();
        panel.reset(settings,
                secretsStore.getOpenAiKey(project).orElse(""),
                secretsStore.getGigaChatKey(project).orElse(""));
    }

    @Override
    public void disposeUIResources() {
        if (panel != null) {
            panel.dispose();
            panel = null;
        }
    }

    private void runConnectionTest() {
        if (panel == null) {
            return;
        }
        TestSmithProjectSettings draftSettings = new TestSmithProjectSettings();
        panel.applyTo(draftSettings);

        com.intellij.openapi.ui.ValidationInfo validation = panel.validateProviderForConnectionTest();
        if (validation != null) {
            LOG.debug("LLM health check not started due to UI validation error: "
                    + SecretSanitizer.sanitize(validation.message));
            if (validation.component != null) {
                validation.component.requestFocusInWindow();
            }
            String message = SecretSanitizer.sanitize(validation.message);
            panel.showConnectionTestFailure(message);
            HealthCheckResult result = HealthCheckResult.failed(
                    providerId(draftSettings.provider),
                    message,
                    "MISCONFIGURED",
                    null
            );
            showConnectionTestDialog(result, draftSettings);
            return;
        }

        panel.setConnectionTestInProgress(true);
        String openAiKey = panel.getOpenAiKey();
        String gigaChatKey = panel.getGigaChatKey();
        LOG.debug("LLM health check requested from settings UI. provider=" + draftSettings.provider);

        AppExecutorUtil.getAppExecutorService().submit(() -> {
            HealthCheckResult result = healthCheckService.healthCheck(draftSettings, openAiKey, gigaChatKey);
            LOG.debug("LLM health check result received in settings UI. provider="
                    + result.providerId()
                    + " status="
                    + result.status()
                    + " technicalCode="
                    + SecretSanitizer.sanitize(result.technicalCode())
                    + " latencyMs="
                    + result.latencyMs());
            ApplicationManager.getApplication().invokeLater(() -> {
                if (panel == null) {
                    return;
                }
                panel.setConnectionTestInProgress(false);
                panel.showConnectionTestResult(result);
                showConnectionTestDialog(result, draftSettings);
            }, ModalityState.any());
        });
    }

    private void showConnectionTestDialog(HealthCheckResult result, TestSmithProjectSettings settings) {
        if (panel == null) {
            return;
        }
        String details = LlmConnectionTestDetailsFormatter.format(settings, result);
        new LlmConnectionTestDialog(project, result, details).show();
    }

    private static String providerId(LlmProvider provider) {
        LlmProvider effectiveProvider = provider == null ? LlmProvider.OLLAMA : provider;
        return effectiveProvider.name();
    }
}
