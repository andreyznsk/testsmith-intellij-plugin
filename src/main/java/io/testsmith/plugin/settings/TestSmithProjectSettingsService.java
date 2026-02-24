package io.testsmith.plugin.settings;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(
        name = "TestSmithProjectSettings",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class TestSmithProjectSettingsService implements com.intellij.openapi.components.PersistentStateComponent<TestSmithProjectSettings> {
    private TestSmithProjectSettings state = new TestSmithProjectSettings();

    public static TestSmithProjectSettingsService getInstance(Project project) {
        return project.getService(TestSmithProjectSettingsService.class);
    }

    @Override
    public @NotNull TestSmithProjectSettings getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull TestSmithProjectSettings state) {
        this.state = state;
    }

    public TestSmithProjectSettings getSettings() {
        return state;
    }
}
