package io.testsmith.plugin.ui.toolwindow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import io.testsmith.plugin.agent.*;
import io.testsmith.plugin.agent.AgentPreflightValidator;
import io.testsmith.plugin.settings.TestSmithProjectSettingsService;
import io.testsmith.plugin.ui.model.AgentUiState;
import io.testsmith.plugin.settings.ui.TestSmithSettingsConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public final class TestSmithToolWindowPanel extends JBPanel<JBPanel<?>> implements Disposable, ApprovalGateway {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Project project;
    private final AgentController controller;
    private final AgentEventListener listener;

    private final JBLabel statusLabel = new JBLabel("State: IDLE");
    private final JBLabel modeLabel = new JBLabel("Mode: Manual");
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");
    private final JButton settingsButton = new JButton("Settings");
    private final JButton approveButton = new JButton("Approve");
    private final JButton editButton = new JButton("Edit");
    private final JButton rejectButton = new JButton("Reject");
    private final JBTextArea logsArea = new JBTextArea();
    private final JBTextArea diffArea = new JBTextArea();
    private final JBLabel targetClassLabel = new JBLabel("Target: --");
    private final JBLabel testClassLabel = new JBLabel("Proposed Test: --");
    private final JBLabel confidenceLabel = new JBLabel("Confidence: --");
    private final JBLabel requiresInfrastructureLabel = new JBLabel("Requires Infrastructure: --");
    private final JBLabel diffModeLabel = new JBLabel("Diff Preview: Full file replace");

    private final Object approvalLock = new Object();
    private @Nullable ApprovalRequest pendingRequest;
    private @Nullable CompletableFuture<ApprovalDecision> pendingDecision;
    private @Nullable Map<Path, String> pendingEditedFiles;
    private volatile boolean preflightFailed;

    public TestSmithToolWindowPanel(@NotNull Project project, @NotNull AgentController controller) {
        super(new BorderLayout(JBUI.scale(8), JBUI.scale(8)));
        this.project = Objects.requireNonNull(project, "project");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.listener = (state, event) -> ApplicationManager.getApplication().invokeLater(this::refresh);

        setBorder(JBUI.Borders.empty(8));
        add(createHeader(), BorderLayout.NORTH);
        add(createMainPanel(), BorderLayout.CENTER);
        bindActions();
        controller.setApprovalGateway(this);
        controller.addListener(listener);

        refresh();
    }

    private @NotNull JComponent createHeader() {
        JPanel header = new JBPanel<>(new BorderLayout());

        JPanel statusPanel = new JBPanel<>();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.add(statusLabel);
        statusPanel.add(Box.createVerticalStrut(JBUI.scale(4)));
        statusPanel.add(modeLabel);

        JPanel buttons = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
        buttons.add(runButton);
        buttons.add(stopButton);
        buttons.add(settingsButton);

        header.add(statusPanel, BorderLayout.WEST);
        header.add(buttons, BorderLayout.EAST);
        return header;
    }

    private @NotNull JComponent createMainPanel() {
        JPanel main = new JBPanel<>(new BorderLayout(JBUI.scale(8), JBUI.scale(8)));
        main.add(createApprovalPanel(), BorderLayout.NORTH);
        main.add(createLogsPanel(), BorderLayout.CENTER);
        return main;
    }

    private @NotNull JComponent createApprovalPanel() {
        JPanel approvalPanel = new JBPanel<>(new BorderLayout(JBUI.scale(8), JBUI.scale(8)));
        approvalPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Manual Approval"));

        JPanel meta = new JBPanel<>();
        meta.setLayout(new BoxLayout(meta, BoxLayout.Y_AXIS));
        meta.add(targetClassLabel);
        meta.add(Box.createVerticalStrut(JBUI.scale(2)));
        meta.add(testClassLabel);
        meta.add(Box.createVerticalStrut(JBUI.scale(2)));
        meta.add(confidenceLabel);
        meta.add(Box.createVerticalStrut(JBUI.scale(2)));
        meta.add(requiresInfrastructureLabel);
        meta.add(Box.createVerticalStrut(JBUI.scale(2)));
        meta.add(diffModeLabel);

        JPanel buttons = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        buttons.add(approveButton);
        editButton.setText("Edit (first file, MVP)");
        buttons.add(editButton);
        buttons.add(rejectButton);
        meta.add(Box.createVerticalStrut(JBUI.scale(6)));
        meta.add(buttons);

        diffArea.setEditable(false);
        diffArea.setLineWrap(false);
        diffArea.setWrapStyleWord(false);
        diffArea.setRows(12);

        approvalPanel.add(meta, BorderLayout.NORTH);
        approvalPanel.add(new JBScrollPane(diffArea), BorderLayout.CENTER);
        return approvalPanel;
    }

    private @NotNull JComponent createLogsPanel() {
        logsArea.setEditable(false);
        logsArea.setLineWrap(true);
        logsArea.setWrapStyleWord(true);

        JPanel logsPanel = new JBPanel<>(new BorderLayout());
        logsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Agent Events"));
        logsPanel.add(new JBScrollPane(logsArea), BorderLayout.CENTER);
        return logsPanel;
    }

    private void bindActions() {
        runButton.addActionListener(event -> startFromUi());
        stopButton.addActionListener(event -> controller.requestStop());
        settingsButton.addActionListener(event ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, TestSmithSettingsConfigurable.class));
        approveButton.addActionListener(event -> approvePending());
        editButton.addActionListener(event -> editPending());
        rejectButton.addActionListener(event -> rejectPending());
    }

    private void refresh() {
        AgentUiState uiState = toUiState(controller.getState(), preflightFailed);
        statusLabel.setText("State: " + uiState);

        String mode = TestSmithProjectSettingsService.getInstance(project).getSettings().executionMode.toString();
        modeLabel.setText("Mode: " + mode);

        runButton.setEnabled(uiState == AgentUiState.IDLE || uiState == AgentUiState.ERROR);
        stopButton.setEnabled(uiState == AgentUiState.RUNNING || uiState == AgentUiState.STOPPING);

        refreshApprovalFields(controller.getState());

        List<AgentEvent> events = controller.getRecentEvents();
        StringBuilder builder = new StringBuilder();
        for (AgentEvent event : events) {
            String timestamp = LOG_TIME.format(event.at().atZone(ZoneId.systemDefault()));
            builder.append('[')
                    .append(timestamp)
                    .append("] ")
                    .append(event.type())
                    .append(" - ")
                    .append(event.message())
                    .append('\n');
        }
        logsArea.setText(builder.toString());
        logsArea.setCaretPosition(logsArea.getDocument().getLength());
    }

    private void startFromUi() {
        Optional<String> error = AgentPreflightValidator.validate(project);
        if (error.isPresent()) {
            preflightFailed = true;
            refresh();
            Messages.showErrorDialog(project, error.get(), "TestSmith Run Validation");
            return;
        }
        preflightFailed = false;
        controller.start();
        refresh();
    }

    private static @NotNull AgentUiState toUiState(@NotNull AgentState state, boolean preflightFailed) {
        if (preflightFailed && state == AgentState.IDLE) {
            return AgentUiState.ERROR;
        }
        return switch (state) {
            case IDLE -> AgentUiState.IDLE;
            case RUNNING, WAITING_FOR_APPROVAL -> AgentUiState.RUNNING;
            case STOPPING -> AgentUiState.STOPPING;
            case ERROR -> AgentUiState.ERROR;
        };
    }

    private void refreshApprovalFields(AgentState state) {
        ApprovalRequest request;
        synchronized (approvalLock) {
            request = pendingRequest;
        }
        boolean waiting = state == AgentState.WAITING_FOR_APPROVAL && request != null;
        approveButton.setEnabled(waiting);
        editButton.setEnabled(waiting);
        rejectButton.setEnabled(waiting);

        if (!waiting) {
            targetClassLabel.setText("Target: --");
            testClassLabel.setText("Proposed Test: --");
            confidenceLabel.setText("Confidence: --");
            requiresInfrastructureLabel.setText("Requires Infrastructure: --");
            diffArea.setText("");
            return;
        }

        targetClassLabel.setText("Target: " + request.targetClassFqn());
        testClassLabel.setText("Proposed Test: " + request.testClassFqn());
        Double confidence = request.confidence();
        confidenceLabel.setText(confidence == null ? "Confidence: --" : "Confidence: " + String.format("%.2f", confidence));
        requiresInfrastructureLabel.setText("Requires Infrastructure: " + request.requiresInfrastructure());
        diffArea.setText(request.diffText());
        diffArea.setCaretPosition(0);
    }

    @Override
    public @NotNull CompletableFuture<ApprovalDecision> requestApproval(@NotNull ApprovalRequest request) {
        CompletableFuture<ApprovalDecision> future = new CompletableFuture<>();
        synchronized (approvalLock) {
            pendingRequest = request;
            pendingDecision = future;
            pendingEditedFiles = new LinkedHashMap<>(request.proposedFiles());
        }
        ApplicationManager.getApplication().invokeLater(this::refresh);
        return future;
    }

    @Override
    public void cancel(@NotNull UUID runId) {
        CompletableFuture<ApprovalDecision> toCancel = null;
        synchronized (approvalLock) {
            if (pendingRequest != null && runId.equals(pendingRequest.runId())) {
                toCancel = pendingDecision;
                pendingRequest = null;
                pendingDecision = null;
                pendingEditedFiles = null;
            }
        }
        if (toCancel != null) {
            toCancel.completeExceptionally(new CancellationException("Approval cancelled: " + runId));
        }
        ApplicationManager.getApplication().invokeLater(this::refresh);
    }

    private void approvePending() {
        CompletableFuture<ApprovalDecision> future;
        Map<Path, String> editedFiles;
        synchronized (approvalLock) {
            future = pendingDecision;
            editedFiles = pendingEditedFiles == null ? Map.of() : Map.copyOf(pendingEditedFiles);
            pendingRequest = null;
            pendingDecision = null;
            pendingEditedFiles = null;
        }
        if (future != null) {
            future.complete(new ApprovalDecision(DecisionType.APPROVE, editedFiles));
        }
        refresh();
    }

    private void rejectPending() {
        CompletableFuture<ApprovalDecision> future;
        synchronized (approvalLock) {
            future = pendingDecision;
            pendingRequest = null;
            pendingDecision = null;
            pendingEditedFiles = null;
        }
        if (future != null) {
            future.complete(ApprovalDecision.reject());
        }
        refresh();
    }

    private void editPending() {
        ApprovalRequest request;
        Map<Path, String> editable;
        synchronized (approvalLock) {
            request = pendingRequest;
            editable = pendingEditedFiles == null ? null : new LinkedHashMap<>(pendingEditedFiles);
        }
        if (request == null || editable == null || editable.isEmpty()) {
            return;
        }
        Map.Entry<Path, String> first = editable.entrySet().iterator().next();
        String edited = Messages.showMultilineInputDialog(
                project,
                "Edit proposed content for:\n" + first.getKey(),
                "Edit Generated Test",
                first.getValue(),
                null,
                null
        );
        if (edited == null) {
            return;
        }
        synchronized (approvalLock) {
            if (pendingEditedFiles != null && pendingRequest != null && pendingRequest.runId().equals(request.runId())) {
                pendingEditedFiles.put(first.getKey(), edited);
            }
        }
        refresh();
    }

    @Override
    public void dispose() {
        CompletableFuture<ApprovalDecision> future;
        synchronized (approvalLock) {
            future = pendingDecision;
            pendingRequest = null;
            pendingDecision = null;
            pendingEditedFiles = null;
        }
        if (future != null) {
            future.completeExceptionally(new CancellationException("Tool window disposed"));
        }
        controller.setApprovalGateway(ApprovalGateway.rejecting());
        controller.removeListener(listener);
    }
}
