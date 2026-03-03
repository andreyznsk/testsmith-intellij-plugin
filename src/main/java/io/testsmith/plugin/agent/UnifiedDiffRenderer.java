package io.testsmith.plugin.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UnifiedDiffRenderer {
    public @NotNull String renderFileDiff(@NotNull Path path, @Nullable String oldContent, @NotNull String newContent) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(newContent, "newContent");
        List<String> oldLines = splitLines(oldContent);
        List<String> newLines = splitLines(newContent);

        String fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        StringBuilder diff = new StringBuilder();
        diff.append("--- a/").append(fileName).append('\n');
        diff.append("+++ b/").append(fileName).append('\n');
        diff.append("@@ -1,").append(Math.max(oldLines.size(), 1))
                .append(" +1,").append(Math.max(newLines.size(), 1)).append(" @@\n");

        if (oldLines.isEmpty()) {
            for (String newLine : newLines) {
                diff.append('+').append(newLine).append('\n');
            }
            return diff.toString();
        }

        for (String oldLine : oldLines) {
            diff.append('-').append(oldLine).append('\n');
        }
        for (String newLine : newLines) {
            diff.append('+').append(newLine).append('\n');
        }
        return diff.toString();
    }

    private static List<String> splitLines(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>(parts.length);
        for (String part : parts) {
            lines.add(part);
        }
        return lines;
    }
}
