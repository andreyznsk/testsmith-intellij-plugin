package io.testsmith.plugin.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import io.testsmith.plugin.agent.AgentControllerService;
import io.testsmith.plugin.agent.AgentState;
import org.jetbrains.annotations.NotNull;

public final class RunAgentAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        project.getService(AgentControllerService.class).start();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            event.getPresentation().setEnabled(false);
            return;
        }

        AgentState state = project.getService(AgentControllerService.class).getState();
        boolean enabled = state == AgentState.IDLE || state == AgentState.ERROR;
        event.getPresentation().setEnabled(enabled);
    }
}
