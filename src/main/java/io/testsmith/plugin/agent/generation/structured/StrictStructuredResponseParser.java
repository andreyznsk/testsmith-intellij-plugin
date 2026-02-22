package io.testsmith.plugin.agent.generation.structured;

import io.testsmith.plugin.llm.internal.JsonCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StrictStructuredResponseParser implements StructuredResponseParser {
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_ACTION = "action";
    private static final String FIELD_TARGET_CLASS = "targetClass";
    private static final String FIELD_TEST_CLASS_NAME = "testClassName";
    private static final String FIELD_IMPORTS = "imports";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_ASSUMPTIONS = "assumptions";
    private static final String FIELD_REQUIRES_INFRA = "requiresInfrastructure";
    private static final String FIELD_CONFIDENCE = "confidence";
    private static final String FIELD_METADATA = "metadata";

    private static final Set<String> REQUIRED_FIELDS = Set.of(
            FIELD_VERSION,
            FIELD_ACTION,
            FIELD_TARGET_CLASS,
            FIELD_TEST_CLASS_NAME,
            FIELD_IMPORTS,
            FIELD_CODE,
            FIELD_ASSUMPTIONS,
            FIELD_REQUIRES_INFRA
    );

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            FIELD_VERSION,
            FIELD_ACTION,
            FIELD_TARGET_CLASS,
            FIELD_TEST_CLASS_NAME,
            FIELD_IMPORTS,
            FIELD_CODE,
            FIELD_ASSUMPTIONS,
            FIELD_REQUIRES_INFRA,
            FIELD_CONFIDENCE,
            FIELD_METADATA
    );

    @Override
    public StructuredTest parse(String raw) {
        if (raw == null) {
            throw new StructuredResponseException(ValidationErrorType.STRUCTURE_INVALID, "LLM response must not be null");
        }

        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new StructuredResponseException(
                    ValidationErrorType.STRUCTURE_INVALID,
                    "LLM response must be exactly one JSON object without wrapper prose"
            );
        }

        Object root;
        try {
            root = JsonCodec.parse(trimmed);
        } catch (RuntimeException ex) {
            throw new StructuredResponseException(ValidationErrorType.STRUCTURE_INVALID, "Invalid JSON response", ex);
        }

        if (!(root instanceof Map<?, ?> rawMap)) {
            throw new StructuredResponseException(ValidationErrorType.STRUCTURE_INVALID, "Top-level JSON must be an object");
        }

        Map<String, Object> payload = castToStringObjectMap(rawMap);
        ensureFields(payload);

        String version = requireString(payload, FIELD_VERSION);
        StructuredAction action = requireAction(payload, FIELD_ACTION);
        String targetClass = requireString(payload, FIELD_TARGET_CLASS);
        String testClassName = requireString(payload, FIELD_TEST_CLASS_NAME);
        List<String> imports = requireStringArray(payload, FIELD_IMPORTS);
        String code = requireString(payload, FIELD_CODE);
        List<String> assumptions = requireStringArray(payload, FIELD_ASSUMPTIONS);
        boolean requiresInfrastructure = requireBoolean(payload, FIELD_REQUIRES_INFRA);
        Double confidence = requireOptionalDouble(payload, FIELD_CONFIDENCE);
        Map<String, Object> metadata = requireOptionalObject(payload, FIELD_METADATA);

        return new StructuredTest(
                version,
                action,
                targetClass,
                testClassName,
                imports,
                code,
                assumptions,
                requiresInfrastructure,
                confidence,
                metadata
        );
    }

    private static void ensureFields(Map<String, Object> payload) {
        for (String required : REQUIRED_FIELDS) {
            if (!payload.containsKey(required)) {
                throw new StructuredResponseException(
                        ValidationErrorType.SCHEMA_INVALID,
                        "Missing required field: " + required
                );
            }
        }

        for (String key : payload.keySet()) {
            if (!ALLOWED_FIELDS.contains(key)) {
                throw new StructuredResponseException(
                        ValidationErrorType.SCHEMA_INVALID,
                        "Unknown field in structured response: " + key
                );
            }
        }
    }

    private static Map<String, Object> castToStringObjectMap(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String stringKey)) {
                throw new StructuredResponseException(
                        ValidationErrorType.SCHEMA_INVALID,
                        "Structured response contains non-string object key"
                );
            }
            result.put(stringKey, entry.getValue());
        }
        return result;
    }

    private static StructuredAction requireAction(Map<String, Object> payload, String field) {
        String raw = requireString(payload, field);
        try {
            return StructuredAction.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new StructuredResponseException(
                    ValidationErrorType.SCHEMA_INVALID,
                    "Field '" + field + "' has unsupported value: " + raw,
                    ex
            );
        }
    }

    private static String requireString(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (!(value instanceof String stringValue)) {
            throw new StructuredResponseException(
                    ValidationErrorType.SCHEMA_INVALID,
                    "Field '" + field + "' must be a string"
            );
        }
        return stringValue;
    }

    private static List<String> requireStringArray(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (!(value instanceof List<?> listValue)) {
            throw new StructuredResponseException(
                    ValidationErrorType.SCHEMA_INVALID,
                    "Field '" + field + "' must be an array"
            );
        }
        List<String> result = new ArrayList<>(listValue.size());
        for (Object item : listValue) {
            if (!(item instanceof String stringItem)) {
                throw new StructuredResponseException(
                        ValidationErrorType.SCHEMA_INVALID,
                        "Field '" + field + "' must contain only strings"
                );
            }
            result.add(stringItem);
        }
        return List.copyOf(result);
    }

    private static boolean requireBoolean(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (!(value instanceof Boolean boolValue)) {
            throw new StructuredResponseException(
                    ValidationErrorType.SCHEMA_INVALID,
                    "Field '" + field + "' must be a boolean"
            );
        }
        return boolValue;
    }

    private static Double requireOptionalDouble(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new StructuredResponseException(
                    ValidationErrorType.SCHEMA_INVALID,
                    "Field '" + field + "' must be numeric when present"
            );
        }
        return number.doubleValue();
    }

    private static Map<String, Object> requireOptionalObject(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> objectValue)) {
            throw new StructuredResponseException(
                    ValidationErrorType.SCHEMA_INVALID,
                    "Field '" + field + "' must be an object when present"
            );
        }
        return castToStringObjectMap(objectValue);
    }
}
