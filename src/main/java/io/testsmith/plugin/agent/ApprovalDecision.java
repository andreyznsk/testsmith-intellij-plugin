package io.testsmith.plugin.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ApprovalDecision {
    private final DecisionType type;
    private final Map<Path, String> editedFiles;

    public ApprovalDecision(@NotNull DecisionType type, @Nullable Map<Path, String> editedFiles) {
        this.type = Objects.requireNonNull(type, "type");
        this.editedFiles = normalizeFiles(editedFiles);
    }

    public static @NotNull ApprovalDecision approve(@Nullable Map<Path, String> editedFiles) {
        return new ApprovalDecision(DecisionType.APPROVE, editedFiles);
    }

    public static @NotNull ApprovalDecision reject() {
        return new ApprovalDecision(DecisionType.REJECT, null);
    }

    public @NotNull DecisionType type() {
        return type;
    }

    public @NotNull Map<Path, String> editedFiles() {
        return editedFiles;
    }

    private static Map<Path, String> normalizeFiles(@Nullable Map<Path, String> files) {
        if (files == null || files.isEmpty()) {
            return Map.of();
        }
        Map<Path, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<Path, String> entry : files.entrySet()) {
            Path path = Objects.requireNonNull(entry.getKey(), "editedFiles path must not be null")
                    .toAbsolutePath()
                    .normalize();
            String content = Objects.requireNonNull(entry.getValue(), "editedFiles content must not be null");
            normalized.put(path, content);
        }
        return Map.copyOf(normalized);
    }
}
