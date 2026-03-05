package io.testsmith.plugin.startup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public final class TestSmithStartupActivity implements StartupActivity.DumbAware {
    private static final Logger LOG = Logger.getInstance(TestSmithStartupActivity.class);

    @Override
    public void runActivity(@NotNull Project project) {
        LOG.debug("TestSmith plugin started and ready to work. project=" + project.getName());
    }
}
