package io.testsmith.plugin.llm.prompt.response;

/**
 * Canonical LLM response contract for test generation.
 */
public final class LlmResponseContractV1 {
    private LlmResponseContractV1() {
    }

    public static final String FIELD_TEST_CLASS_FQCN = "testClassFqcn";
    public static final String FIELD_SUGGESTED_FILE_PATH = "suggestedFilePath";
    public static final String FIELD_TEST_FRAMEWORK = "testFramework";
    public static final String FIELD_JAVA_SOURCE = "javaSource";
    public static final String FIELD_NOTES = "notes";
}
