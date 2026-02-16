package io.testsmith.plugin.llm.prompt.response;

import io.testsmith.plugin.llm.api.LlmProtocolException;
import io.testsmith.plugin.llm.api.LlmResponse;
import io.testsmith.plugin.llm.api.TestFramework;
import io.testsmith.plugin.llm.internal.JsonCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict parser for the canonical LLM response JSON contract.
 *
 * <p>Rules:
 * <ul>
 *   <li>Top-level must be a single JSON object with no leading/trailing prose.</li>
 *   <li>Required fields: testClassFqcn, suggestedFilePath, testFramework, javaSource, notes.</li>
 *   <li>No unknown top-level fields are allowed.</li>
 *   <li>javaSource must contain Java source only (no markdown fences).</li>
 * </ul>
 */
public final class LlmResponseParser {
    public LlmResponse parse(String rawText) {
        if (rawText == null) {
            throw new LlmProtocolException("LLM response must not be null");
        }
        String trimmed = rawText.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new LlmProtocolException("LLM response must be a single JSON object without wrapper text");
        }

        Object root = JsonCodec.parse(trimmed);
        if (!(root instanceof Map<?, ?> rawMap)) {
            throw new LlmProtocolException("Top-level LLM response JSON must be an object");
        }

        Map<String, Object> payload = castToStringObjectMap(rawMap);
        ensureExpectedFields(payload);

        String testClassFqcn = requireString(payload, LlmResponseContractV1.FIELD_TEST_CLASS_FQCN);
        String suggestedFilePath = requireString(payload, LlmResponseContractV1.FIELD_SUGGESTED_FILE_PATH);
        String testFrameworkRaw = requireString(payload, LlmResponseContractV1.FIELD_TEST_FRAMEWORK);
        String javaSource = requireString(payload, LlmResponseContractV1.FIELD_JAVA_SOURCE);
        List<String> notes = requireStringArray(payload, LlmResponseContractV1.FIELD_NOTES);

        if (javaSource.contains("```")) {
            throw new LlmProtocolException("javaSource must not contain markdown code fences");
        }

        TestFramework testFramework;
        try {
            testFramework = TestFramework.valueOf(testFrameworkRaw);
        } catch (IllegalArgumentException ex) {
            throw new LlmProtocolException("Unsupported testFramework value: " + testFrameworkRaw, ex);
        }

        return new LlmResponse(testClassFqcn, suggestedFilePath, testFramework, javaSource, notes);
    }

    private static void ensureExpectedFields(Map<String, Object> payload) {
        List<String> required = List.of(
                LlmResponseContractV1.FIELD_TEST_CLASS_FQCN,
                LlmResponseContractV1.FIELD_SUGGESTED_FILE_PATH,
                LlmResponseContractV1.FIELD_TEST_FRAMEWORK,
                LlmResponseContractV1.FIELD_JAVA_SOURCE,
                LlmResponseContractV1.FIELD_NOTES
        );

        for (String key : required) {
            if (!payload.containsKey(key)) {
                throw new LlmProtocolException("Missing required field: " + key);
            }
        }

        for (String key : payload.keySet()) {
            if (!required.contains(key)) {
                throw new LlmProtocolException("Unknown field in LLM response: " + key);
            }
        }
    }

    private static Map<String, Object> castToStringObjectMap(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String keyString)) {
                throw new LlmProtocolException("LLM response object contains a non-string key");
            }
            result.put(keyString, entry.getValue());
        }
        return result;
    }

    private static String requireString(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (!(value instanceof String stringValue)) {
            throw new LlmProtocolException("Field '" + field + "' must be a string");
        }
        return stringValue;
    }

    private static List<String> requireStringArray(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (!(value instanceof List<?> listValue)) {
            throw new LlmProtocolException("Field '" + field + "' must be an array");
        }
        List<String> result = new ArrayList<>(listValue.size());
        for (Object item : listValue) {
            if (!(item instanceof String stringItem)) {
                throw new LlmProtocolException(
                        "Field '" + field + "' must contain only strings, but found "
                                + (item == null ? "null" : item.getClass().getSimpleName())
                );
            }
            result.add(stringItem);
        }
        return List.copyOf(result);
    }
}
