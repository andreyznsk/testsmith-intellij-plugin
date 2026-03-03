package io.testsmith.plugin.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ApprovalRequest {
    private final UUID runId;
    private final String targetClassFqn;
    private final String testClassFqn;
    private final String diffText;
    private final Map<Path, String> proposedFiles;
    private final Double confidence;
    private final boolean requiresInfrastructure;

    public ApprovalRequest(
            @NotNull UUID runId,
            @NotNull String targetClassFqn,
            @NotNull String testClassFqn,
            @NotNull String diffText,
            @NotNull Map<Path, String> proposedFiles,
            @Nullable Double confidence,
            boolean requiresInfrastructure
    ) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.targetClassFqn = requireNonBlank(targetClassFqn, "targetClassFqn");
        this.testClassFqn = requireNonBlank(testClassFqn, "testClassFqn");
        this.diffText = Objects.requireNonNull(diffText, "diffText");
        this.proposedFiles = normalizeFiles(proposedFiles);
        this.confidence = confidence;
        this.requiresInfrastructure = requiresInfrastructure;
    }

    public @NotNull UUID runId() {
        return runId;
    }

    public @NotNull String targetClassFqn() {
        return targetClassFqn;
    }

    public @NotNull String testClassFqn() {
        return testClassFqn;
    }

    public @NotNull String diffText() {
        return diffText;
    }

    public @NotNull Map<Path, String> proposedFiles() {
        return proposedFiles;
    }

    public @Nullable Double confidence() {
        return confidence;
    }

    public boolean requiresInfrastructure() {
        return requiresInfrastructure;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static Map<Path, String> normalizeFiles(Map<Path, String> files) {
        Objects.requireNonNull(files, "proposedFiles");
        if (files.isEmpty()) {
            throw new IllegalArgumentException("proposedFiles must not be empty");
        }
        Map<Path, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<Path, String> entry : files.entrySet()) {
            Path path = Objects.requireNonNull(entry.getKey(), "proposedFiles path must not be null").toAbsolutePath().normalize();
            String content = Objects.requireNonNull(entry.getValue(), "proposedFiles content must not be null");
            normalized.put(path, content);
        }
        return Map.copyOf(normalized);
    }
}
