package io.testsmith.plugin.agent.selection;

public record ExclusionMatch(Type type, String rule) {
    public enum Type {
        CLASS,
        PACKAGE
    }
}
