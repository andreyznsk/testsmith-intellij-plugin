package io.testsmith.plugin.llm.prompt.response;

/**
 * Canonical LLM structured response contract for Iteration 3.
 */
public final class LlmResponseContractV1 {
    private LlmResponseContractV1() {
    }

    public static final String VERSION = "1.0";
    public static final String VERSION_REPAIR = "1.1";
    public static final String ACTION_GENERATE_TEST = "GENERATE_TEST";
    public static final String ACTION_REPAIR_TEST = "REPAIR_TEST";

    public static final String FIELD_VERSION = "version";
    public static final String FIELD_ACTION = "action";
    public static final String FIELD_TARGET_CLASS = "targetClass";
    public static final String FIELD_TEST_CLASS_NAME = "testClassName";
    public static final String FIELD_IMPORTS = "imports";
    public static final String FIELD_CODE = "code";
    public static final String FIELD_ASSUMPTIONS = "assumptions";
    public static final String FIELD_REQUIRES_INFRASTRUCTURE = "requiresInfrastructure";

    public static final String FIELD_CONFIDENCE = "confidence";
    public static final String FIELD_METADATA = "metadata";
}
