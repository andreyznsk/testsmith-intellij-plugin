package io.testsmith.plugin.settings.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import io.testsmith.plugin.llm.api.HealthCheckResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;

public final class LlmConnectionTestDialog extends DialogWrapper {
    private final String details;
    private final HealthCheckResult result;

    public LlmConnectionTestDialog(
            @Nullable Project project,
            @NotNull HealthCheckResult result,
            @NotNull String details
    ) {
        super(project, true);
        this.result = result;
        this.details = details;
        setTitle("LLM Connection Test");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setPreferredSize(new Dimension(960, 360));
        JBLabel summary = new JBLabel(result.status() == HealthCheckResult.Status.OK ? "Success" : "Failed");
        Icon icon = result.status() == HealthCheckResult.Status.OK
                ? AllIcons.General.InspectionsOK
                : AllIcons.General.Error;
        summary.setIcon(icon);
        panel.add(summary, BorderLayout.NORTH);

        JBTextArea detailsArea = new JBTextArea(details);
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        panel.add(new JBScrollPane(detailsArea), BorderLayout.CENTER);
        return panel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{new CopyAction(), getOKAction()};
    }

    private final class CopyAction extends DialogWrapperAction {
        private CopyAction() {
            super("Copy");
        }

        @Override
        protected void doAction(ActionEvent e) {
            CopyPasteManager.getInstance().setContents(new StringSelection(details));
        }
    }
}
