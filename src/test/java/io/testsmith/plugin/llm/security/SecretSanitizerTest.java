package io.testsmith.plugin.llm.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretSanitizerTest {
    @Test
    void masksBearerToken() {
        String sanitized = SecretSanitizer.sanitize("Authorization: Bearer super-secret-token-value");
        assertTrue(sanitized.contains("Bearer ***"));
        assertFalse(sanitized.contains("super-secret-token-value"));
    }

    @Test
    void masksOpenAiKey() {
        String sanitized = SecretSanitizer.sanitize("key=sk-abcdefghijklmnopqrstuv123456");
        assertFalse(sanitized.contains("sk-abcdefghijklmnopqrstuv123456"));
        assertTrue(sanitized.contains("***"));
    }

    @Test
    void masksApiKeyInQuery() {
        String sanitized = SecretSanitizer.sanitize("https://example.com/v1?api_key=abcdef&x=1");
        assertTrue(sanitized.contains("api_key=***"));
        assertFalse(sanitized.contains("api_key=abcdef"));
    }

    @Test
    void masksLongTokenLikeValues() {
        String secret = "abcdefghijklmnopqrstuvwxyz0123456789";
        String sanitized = SecretSanitizer.sanitize("token=" + secret);
        assertFalse(sanitized.contains(secret));
        assertTrue(sanitized.contains("token=***"));
    }
}
