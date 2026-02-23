package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.StructuredTest;

@FunctionalInterface
public interface RepairDiffRenderer {
    String render(StructuredTest original, StructuredTest repaired);

    static RepairDiffRenderer simpleCodeDiff() {
        return (original, repaired) -> {
            StringBuilder builder = new StringBuilder();
            builder.append("--- original").append('\n');
            builder.append("+++ repaired").append('\n');

            String[] originalLines = original.code().split("\\R", -1);
            String[] repairedLines = repaired.code().split("\\R", -1);
            int max = Math.max(originalLines.length, repairedLines.length);
            for (int i = 0; i < max; i++) {
                String oldLine = i < originalLines.length ? originalLines[i] : null;
                String newLine = i < repairedLines.length ? repairedLines[i] : null;
                if (oldLine != null && newLine != null && oldLine.equals(newLine)) {
                    continue;
                }
                if (oldLine != null) {
                    builder.append('-').append(oldLine).append('\n');
                }
                if (newLine != null) {
                    builder.append('+').append(newLine).append('\n');
                }
            }
            return builder.toString();
        };
    }
}
