package io.testsmith.plugin.agent.generation.structured;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class DefaultStructuredValidator implements StructuredValidator {
    private static final Pattern TYPE_DECLARATION =
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final List<String> PROHIBITED_ASSUMPTION_MARKERS = List.of("imaginary", "invented", "does not exist");

    @Override
    public ValidationResult validate(StructuredTest test, GenerationContext context) {
        Objects.requireNonNull(test, "test must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (!context.requestedVersion().equals(test.version())) {
            return ValidationResult.invalid(
                    ValidationErrorType.SCHEMA_INVALID,
                    "version must equal " + context.requestedVersion()
            );
        }

        if (test.action() != context.requestedAction()) {
            return ValidationResult.invalid(
                    ValidationErrorType.SEMANTIC_INVALID,
                    "action must equal " + context.requestedAction()
            );
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
        String withoutComments = removeComments(code);
        var matcher = TYPE_DECLARATION.matcher(withoutComments);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean containsMarkdownFence(String code) {
        return code.contains("```");
    }

    private static boolean containsProhibitedWrapperText(String code) {
        String normalized = stripLeadingWhitespaceAndComments(code);
        if (normalized.isEmpty()) {
            return true;
        }

        if (startsWithAny(normalized,
                "package ",
                "import ",
                "public ",
                "final ",
                "abstract ",
                "class ",
                "interface ",
                "enum ",
                "record ")) {
            return false;
        }

        if (normalized.startsWith("@")) {
            return containsWrapperTextBetweenAnnotations(normalized);
        }

        return true;
    }

    private static boolean containsWrapperTextBetweenAnnotations(String normalized) {
        boolean inBlockComment = false;
        for (String line : normalized.split("\\R")) {
            String trimmed = line.stripLeading();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }
            if (trimmed.startsWith("*")) {
                continue;
            }
            if (trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("@")) {
                continue;
            }
            if (startsWithAny(trimmed,
                    "package ",
                    "import ",
                    "public ",
                    "final ",
                    "abstract ",
                    "class ",
                    "interface ",
                    "enum ",
                    "record ")) {
                return false;
            }
            return true;
        }
        return false;
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String stripLeadingWhitespaceAndComments(String code) {
        int index = 0;
        int length = code.length();
        while (index < length) {
            while (index < length && Character.isWhitespace(code.charAt(index))) {
                index++;
            }
            if (index >= length) {
                break;
            }

            if (index + 1 < length && code.charAt(index) == '/' && code.charAt(index + 1) == '/') {
                index += 2;
                while (index < length && code.charAt(index) != '\n') {
                    index++;
                }
                continue;
            }

            if (index + 1 < length && code.charAt(index) == '/' && code.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < length && !(code.charAt(index) == '*' && code.charAt(index + 1) == '/')) {
                    index++;
                }
                if (index + 1 >= length) {
                    return "";
                }
                index += 2;
                continue;
            }

            break;
        }
        return code.substring(index);
    }

    private static String removeComments(String code) {
        StringBuilder out = new StringBuilder(code.length());
        int index = 0;
        while (index < code.length()) {
            char current = code.charAt(index);
            if (current == '/' && index + 1 < code.length()) {
                char next = code.charAt(index + 1);
                if (next == '/') {
                    index += 2;
                    while (index < code.length() && code.charAt(index) != '\n') {
                        index++;
                    }
                    continue;
                }
                if (next == '*') {
                    index += 2;
                    while (index + 1 < code.length()
                            && !(code.charAt(index) == '*' && code.charAt(index + 1) == '/')) {
                        index++;
                    }
                    index = Math.min(index + 2, code.length());
                    continue;
                }
            }
            out.append(current);
            index++;
        }
        return out.toString();
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
