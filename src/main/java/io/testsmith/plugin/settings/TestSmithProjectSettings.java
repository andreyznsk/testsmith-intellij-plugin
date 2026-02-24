package io.testsmith.plugin.settings;

import java.util.ArrayList;
import java.util.List;

public class TestSmithProjectSettings {
    public BuildToolMode buildToolMode = BuildToolMode.AUTO;
    public ExecutionMode executionMode = ExecutionMode.MANUAL;
    public int maxIterations = 5;

    public String jacocoXmlPath = "";
    public int targetCoverage = 80;
    public List<String> exclusions = new ArrayList<>();

    public LlmProvider provider = LlmProvider.OLLAMA;
    public OllamaConfig ollama = new OllamaConfig();
    public OpenAiConfig openAi = new OpenAiConfig();
    public GigaChatConfig gigaChat = new GigaChatConfig();

    public boolean strictMode = true;
    public boolean contextCacheEnabled = true;
    public boolean verboseLogging = false;

    public static class OllamaConfig {
        public String baseUrl = "http://localhost:11434";
        public String model = "llama3";
        public double temperature = 0.2;
        public double topP = 0.9;
        public double repeatPenalty = 1.1;
    }

    public static class OpenAiConfig {
        public String model = "gpt-4o-mini";
        public double temperature = 0.2;
        public int maxTokens = 2048;
    }

    public static class GigaChatConfig {
        public String model = "GigaChat";
        public String endpoint = "";
    }
}
