package io.testsmith.plugin.ui.toolwindow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.testsmith.plugin.ui.controller.AgentController;
import io.testsmith.plugin.ui.model.AgentEventListener;
import io.testsmith.plugin.ui.model.AgentUiModel;
import io.testsmith.plugin.ui.model.AgentUiState;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;

public final class TestSmithToolWindowPanel extends JBPanel<JBPanel<?>> implements Disposable {
    private final AgentUiModel model;
    private final AgentController controller;
    private final AgentEventListener modelListener;

    private final JBLabel coverageLabel = new JBLabel("Coverage: --");
    private final JBLabel modeLabel = new JBLabel("Mode: Manual (stub)");

    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");
    private final JButton settingsButton = new JButton("Settings");

    private final JBLabel targetClassLabel = new JBLabel("No target selected");

    private final JPanel proposalPanel = new JBPanel<>(new BorderLayout(JBUI.scale(8), JBUI.scale(8)));
    private final JBTextArea proposalArea = new JBTextArea();
    private final JButton approveButton = new JButton("Approve");
    private final JButton rejectButton = new JButton("Reject");
    private final JButton editButton = new JButton("Edit");

    private final JBTextArea logsArea = new JBTextArea();

    public TestSmithToolWindowPanel(@NotNull AgentUiModel model, @NotNull AgentController controller) {
        super(new BorderLayout(JBUI.scale(8), JBUI.scale(8)));
        this.model = Objects.requireNonNull(model, "model");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.modelListener = () -> ApplicationManager.getApplication().invokeLater(this::refreshFromModel);

        setBorder(JBUI.Borders.empty(8));

        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createMainPanel(), BorderLayout.CENTER);

        bindActions();
        model.addListener(modelListener);

        refreshFromModel();
    }

    private @NotNull JComponent createHeaderPanel() {
        JPanel header = new JBPanel<>(new BorderLayout());

        JPanel statusPanel = new JBPanel<>();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.add(coverageLabel);
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
        JPanel targetPanel = new JBPanel<>(new BorderLayout());
        targetPanel.setBorder(BorderFactory.createTitledBorder("Target Class"));
        targetPanel.add(targetClassLabel, BorderLayout.CENTER);

        proposalArea.setEditable(false);
        proposalArea.setLineWrap(false);
        proposalArea.setRows(8);

        JPanel proposalButtons = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        proposalButtons.add(approveButton);
        proposalButtons.add(rejectButton);
        proposalButtons.add(editButton);

        proposalPanel.setBorder(BorderFactory.createTitledBorder("Proposed Test / Diff"));
        proposalPanel.add(new JBScrollPane(proposalArea), BorderLayout.CENTER);
        proposalPanel.add(proposalButtons, BorderLayout.SOUTH);

        logsArea.setEditable(false);
        logsArea.setLineWrap(true);
        logsArea.setWrapStyleWord(true);

        JPanel logsPanel = new JBPanel<>(new BorderLayout());
        logsPanel.setBorder(BorderFactory.createTitledBorder("Logs"));
        logsPanel.add(new JBScrollPane(logsArea), BorderLayout.CENTER);

        JBSplitter verticalSplitter = new JBSplitter(true, 0.60f);
        verticalSplitter.setFirstComponent(FormBuilder.createFormBuilder()
                .addComponent(targetPanel)
                .addComponent(proposalPanel)
                .getPanel());
        verticalSplitter.setSecondComponent(logsPanel);
        return verticalSplitter;
    }

    private void bindActions() {
        runButton.addActionListener(e -> controller.start());
        stopButton.addActionListener(e -> controller.stop());
        settingsButton.addActionListener(e -> controller.openSettings());
        approveButton.addActionListener(e -> controller.approve());
        rejectButton.addActionListener(e -> controller.reject());
        editButton.addActionListener(e -> controller.editProposal());
    }

    private void refreshFromModel() {
        AgentUiState state = model.getState();
        coverageLabel.setText(model.getCoverageSummary());
        modeLabel.setText("State: " + state.name());

        boolean runEnabled = state == AgentUiState.IDLE || state == AgentUiState.STOPPED || state == AgentUiState.ERROR;
        runButton.setEnabled(runEnabled);

        boolean stopEnabled = state != AgentUiState.IDLE;
        stopButton.setEnabled(stopEnabled);

        boolean waitingForApproval = state == AgentUiState.WAITING_FOR_APPROVAL;
        proposalPanel.setVisible(waitingForApproval);
        approveButton.setEnabled(waitingForApproval);
        rejectButton.setEnabled(waitingForApproval);
        editButton.setEnabled(waitingForApproval);

        String proposalText = model.getProposalText();
        proposalArea.setText(proposalText == null ? "" : proposalText);
        proposalArea.setCaretPosition(0);

        List<String> logs = model.getLogEntries();
        logsArea.setText(String.join("\n", logs));
        logsArea.setCaretPosition(logsArea.getDocument().getLength());

        revalidate();
        repaint();
    }

    @Override
    public void dispose() {
        model.removeListener(modelListener);
        if (controller instanceof Disposable disposableController) {
            disposableController.dispose();
        }
    }
}
