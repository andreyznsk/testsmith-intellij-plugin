package io.testsmith.plugin.agent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SafeTestFileWriter implements TestFileWriter {
    private final Project project;

    public SafeTestFileWriter(@NotNull Project project) {
        this.project = Objects.requireNonNull(project, "project");
    }

    @Override
    public @NotNull Path resolveTestFile(@NotNull String testClassFqn) {
        Objects.requireNonNull(testClassFqn, "testClassFqn");
        String relative = testClassFqn.replace('.', '/') + ".java";
        Path basePath = Path.of(Objects.requireNonNull(project.getBasePath(), "project.basePath"));
        return basePath.resolve("src").resolve("test").resolve("java").resolve(relative).toAbsolutePath().normalize();
    }

    @Override
    public @Nullable String readCurrentContent(@NotNull Path path) throws Exception {
        Path normalized = normalize(path);
        return Files.exists(normalized) ? Files.readString(normalized, StandardCharsets.UTF_8) : null;
    }

    @Override
    public void writeFiles(
            @NotNull Map<Path, String> files,
            @NotNull Map<Path, String> expectedCurrentContent
    ) throws Exception {
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(expectedCurrentContent, "expectedCurrentContent");
        if (files.isEmpty()) {
            return;
        }

        Map<Path, String> normalizedFiles = new LinkedHashMap<>();
        for (Map.Entry<Path, String> entry : files.entrySet()) {
            Path path = normalize(entry.getKey());
            String content = Objects.requireNonNull(entry.getValue(), "file content must not be null");
            assertSafeTarget(path);
            normalizedFiles.put(path, content);
        }

        for (Map.Entry<Path, String> entry : normalizedFiles.entrySet()) {
            Path path = entry.getKey();
            String expected = expectedCurrentContent.get(path);
            String actual = readCurrentContent(path);
            if (!Objects.equals(expected, actual)) {
                throw new WriteSafetyException("File changed on disk before apply: " + path);
            }
        }

        Runnable writeTask = () -> WriteCommandAction.runWriteCommandAction(project, "Apply TestSmith Generated Tests", null, () -> {
            for (Map.Entry<Path, String> entry : normalizedFiles.entrySet()) {
                Path path = entry.getKey();
                String content = entry.getValue();
                try {
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, content, StandardCharsets.UTF_8);
                    refreshVfs(path);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        if (ApplicationManager.getApplication().isDispatchThread()) {
            writeTask.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(writeTask);
        }
    }

    private void refreshVfs(Path path) {
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        if (vf != null) {
            vf.refresh(false, false);
        }
    }

    private void assertSafeTarget(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if ("build.gradle".equals(fileName)
                || "build.gradle.kts".equals(fileName)
                || "pom.xml".equals(fileName)) {
            throw new WriteSafetyException("Blocked file: " + fileName);
        }
        if (!isUnderTestSource(path)) {
            throw new WriteSafetyException("Target is outside test source root: " + path);
        }
    }

    private boolean isUnderTestSource(Path path) {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        Path cursor = path;
        while (cursor != null) {
            VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(cursor.toString());
            if (vf != null && vf.exists()) {
                if (fileIndex.isInTestSourceContent(vf)) {
                    return true;
                }
                break;
            }
            cursor = cursor.getParent();
        }
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/src/test/");
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static void refreshVfs(Path path) {
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        if (vf != null) {
            vf.refresh(false, false);
        }
    }
}
