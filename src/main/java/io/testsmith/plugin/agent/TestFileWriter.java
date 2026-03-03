package io.testsmith.plugin.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;

public interface TestFileWriter {
    @NotNull Path resolveTestFile(@NotNull String testClassFqn);

    @Nullable String readCurrentContent(@NotNull Path path) throws Exception;

    void writeFiles(
            @NotNull Map<Path, String> files,
            @NotNull Map<Path, String> expectedCurrentContent
    ) throws Exception;
}
