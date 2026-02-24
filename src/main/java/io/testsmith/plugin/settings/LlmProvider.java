package io.testsmith.plugin.settings;

public enum LlmProvider {
    OLLAMA("Ollama"),
    OPENAI("OpenAI"),
    GIGACHAT("GigaChat");

    private final String displayName;

    LlmProvider(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
