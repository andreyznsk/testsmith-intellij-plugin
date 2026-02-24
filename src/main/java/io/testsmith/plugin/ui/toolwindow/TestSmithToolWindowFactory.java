package io.testsmith.plugin.ui.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.testsmith.plugin.ui.controller.AgentController;
import io.testsmith.plugin.ui.controller.StubAgentController;
import io.testsmith.plugin.ui.model.AgentUiModel;
import io.testsmith.plugin.ui.model.DefaultAgentUiModel;
import org.jetbrains.annotations.NotNull;

public final class TestSmithToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        AgentUiModel model = new DefaultAgentUiModel();
        AgentController controller = new StubAgentController(project, model);
        TestSmithToolWindowPanel panel = new TestSmithToolWindowPanel(model, controller);

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
    }
}
