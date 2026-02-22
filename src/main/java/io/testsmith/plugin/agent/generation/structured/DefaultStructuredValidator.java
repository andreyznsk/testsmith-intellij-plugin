package io.testsmith.plugin.agent.generation.structured;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class DefaultStructuredValidator implements StructuredValidator {
    private static final Pattern CLASS_DECLARATION = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final List<String> PROHIBITED_ASSUMPTION_MARKERS = List.of("imaginary", "invented", "does not exist");

    @Override
    public ValidationResult validate(StructuredTest test, GenerationContext context) {
        Objects.requireNonNull(test, "test must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (!StructuredTest.VERSION_1_0.equals(test.version())) {
            return ValidationResult.invalid(ValidationErrorType.SCHEMA_INVALID, "version must equal 1.0");
        }

        if (test.action() != context.requestedAction()) {
            return ValidationResult.invalid(ValidationErrorType.SEMANTIC_INVALID, "action does not match requested action");
        }

        if (!test.targetClass().equals(context.requestedTargetClass())) {
            return ValidationResult.invalid(ValidationErrorType.SEMANTIC_INVALID, "targetClass does not match requested target class");
        }

        if (!test.testClassName().endsWith("Test")) {
            return ValidationResult.invalid(ValidationErrorType.SEMANTIC_INVALID, "testClassName must end with 'Test'");
        }

        String classNameInCode = extractClassName(test.code());
        if (classNameInCode == null) {
            return ValidationResult.invalid(ValidationErrorType.SEMANTIC_INVALID, "code must contain a class declaration");
        }
        if (!classNameInCode.equals(test.testClassName())) {
            return ValidationResult.invalid(ValidationErrorType.SEMANTIC_INVALID, "class declaration must match testClassName");
        }

        if (containsMarkdownFence(test.code())) {
            return ValidationResult.invalid(ValidationErrorType.SEMANTIC_INVALID, "code must not contain markdown fences");
        }

        if (containsProhibitedWrapperText(test.code())) {
            return ValidationResult.invalid(ValidationErrorType.SEMANTIC_INVALID, "code contains wrapper prose");
        }

        if (containsProhibitedAssumptions(test.assumptions())) {
            return ValidationResult.invalid(ValidationErrorType.SEMANTIC_INVALID, "assumptions contain imaginary dependencies");
        }

        if (!context.discoveredAnnotations().contains("@SpringBootTest") && test.code().contains("@SpringBootTest")) {
            return ValidationResult.invalid(ValidationErrorType.SEMANTIC_INVALID, "@SpringBootTest is not allowed unless discovered");
        }

        if (test.requiresInfrastructure()) {
            return ValidationResult.invalid(
                    ValidationErrorType.INFRASTRUCTURE_REQUIRED,
                    "requiresInfrastructure=true is blocked in Iteration 3"
            );
        }

        return ValidationResult.ok();
    }

    private static String extractClassName(String code) {
        var matcher = CLASS_DECLARATION.matcher(code);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean containsMarkdownFence(String code) {
        return code.contains("```");
    }

    private static boolean containsProhibitedWrapperText(String code) {
        String trimmed = code.stripLeading();
        return trimmed.startsWith("Here is") || trimmed.startsWith("The following") || trimmed.startsWith("This test");
    }

    private static boolean containsProhibitedAssumptions(List<String> assumptions) {
        for (String assumption : assumptions) {
            String normalized = assumption.toLowerCase();
            for (String marker : PROHIBITED_ASSUMPTION_MARKERS) {
                if (normalized.contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }
}
