package io.testsmith.plugin.testrunner.failure;

import java.util.ArrayList;
import java.util.List;

public final class LogSlicer {
    private LogSlicer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static List<String> toLines(String raw) {
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] split = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>(split.length);
        for (String line : split) {
            lines.add(line);
        }
        return trimTrailingEmpty(lines);
    }

    public static List<String> combinedLines(String stdout, String stderr) {
        String combined = normalize(stdout) + "\n" + normalize(stderr);
        return toLines(combined);
    }

    public static List<String> tail(String stdout, String stderr, int maxLines) {
        return tail(combinedLines(stdout, stderr), maxLines);
    }

    public static List<String> tail(List<String> lines, int maxLines) {
        if (lines.isEmpty() || maxLines <= 0) {
            return List.of();
        }
        int start = Math.max(0, lines.size() - maxLines);
        return List.copyOf(lines.subList(start, lines.size()));
    }

    public static List<String> slice(List<String> lines, int startIndex, int maxLines) {
        if (lines.isEmpty() || maxLines <= 0) {
            return List.of();
        }
        int start = Math.max(0, startIndex);
        if (start >= lines.size()) {
            return List.of();
        }
        int end = Math.min(lines.size(), start + maxLines);
        return List.copyOf(lines.subList(start, end));
    }

    public static List<String> limit(List<String> lines, int maxLines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        return lines.size() <= maxLines ? List.copyOf(lines) : List.copyOf(lines.subList(0, maxLines));
    }

    private static List<String> trimTrailingEmpty(List<String> lines) {
        int end = lines.size();
        while (end > 0) {
            String line = lines.get(end - 1);
            if (line != null && !line.trim().isEmpty()) {
                break;
            }
            end--;
        }
        if (end == lines.size()) {
            return List.copyOf(lines);
        }
        return end == 0 ? List.of() : List.copyOf(lines.subList(0, end));
    }
}
