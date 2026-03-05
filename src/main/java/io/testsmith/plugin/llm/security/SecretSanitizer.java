package io.testsmith.plugin.llm.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class SecretSanitizer {
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-+/=]{8,}");
    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("\\bsk-[A-Za-z0-9\\-_]{8,}\\b");
    private static final Pattern API_KEY_QUERY_PATTERN = Pattern.compile("(?i)(api[_-]?key\\s*=\\s*)([^&\\s]+)");
    private static final Pattern TOKEN_QUERY_PATTERN = Pattern.compile("(?i)(token\\s*=\\s*)([^&\\s]+)");
    private static final Pattern URL_USERINFO_PATTERN = Pattern.compile("(?i)(https?://)([^/@\\s:]+):([^/@\\s]+)@");
    private static final Pattern LONG_SECRET_PATTERN = Pattern.compile("\\b[A-Za-z0-9\\-_]{24,}\\b");

    private SecretSanitizer() {
    }

    public static @NotNull String sanitize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value;
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer ***");
        sanitized = OPENAI_KEY_PATTERN.matcher(sanitized).replaceAll("***");
        sanitized = API_KEY_QUERY_PATTERN.matcher(sanitized).replaceAll("$1***");
        sanitized = TOKEN_QUERY_PATTERN.matcher(sanitized).replaceAll("$1***");
        sanitized = URL_USERINFO_PATTERN.matcher(sanitized).replaceAll("$1***:***@");
        sanitized = LONG_SECRET_PATTERN.matcher(sanitized).replaceAll("***");
        return sanitized;
    }
}
