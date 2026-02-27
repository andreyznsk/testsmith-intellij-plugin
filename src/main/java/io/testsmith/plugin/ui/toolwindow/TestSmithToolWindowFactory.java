package io.testsmith.plugin.ui.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.testsmith.plugin.agent.AgentController;
import io.testsmith.plugin.agent.AgentControllerService;
import org.jetbrains.annotations.NotNull;

public final class TestSmithToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        AgentController controller = project.getService(AgentControllerService.class);
        TestSmithToolWindowPanel panel = new TestSmithToolWindowPanel(project, controller);

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
    }
}
