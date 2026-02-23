package io.testsmith.plugin.agent.generation.structured;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StructuredTest(
        String version,
        StructuredAction action,
        String targetClass,
        String testClassName,
        List<String> imports,
        String code,
        List<String> assumptions,
        boolean requiresInfrastructure,
        Double confidence,
        Map<String, Object> metadata
) {
    public static final String VERSION_1_0 = "1.0";
    public static final String VERSION_1_1 = "1.1";

    public StructuredTest {
        version = requireNonBlank(version, "version");
        Objects.requireNonNull(action, "action must not be null");
        targetClass = requireNonBlank(targetClass, "targetClass");
        testClassName = requireNonBlank(testClassName, "testClassName");
        code = requireNonBlank(code, "code");

        imports = imports == null ? List.of() : List.copyOf(imports);
        imports.forEach(imp -> requireNonBlank(imp, "imports item"));

        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        assumptions.forEach(assumption -> requireNonBlank(assumption, "assumptions item"));

        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
        metadata.forEach((key, value) -> {
            requireNonBlank(key, "metadata key");
            Objects.requireNonNull(value, "metadata value must not be null");
        });

        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            throw new IllegalArgumentException("confidence must be within [0, 1]");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
