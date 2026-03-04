package io.testsmith.plugin.llm.openai;

import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.HealthCheckResult;
import io.testsmith.plugin.llm.api.LlmException;
import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.security.SecretSanitizer;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;

public final class OpenAiLlmClient implements LlmClient {
    private static final String PROVIDER_ID = "OPENAI";
    private static final Duration HEALTH_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);

    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final HttpClient healthHttpClient;
    private final String apiBaseUrl;
    private final Duration healthTimeout;

    public OpenAiLlmClient(String apiKey, String model, double temperature, int maxTokens) {
        this(
                apiKey,
                model,
                temperature,
                maxTokens,
                HttpClient.newBuilder().connectTimeout(HEALTH_CONNECT_TIMEOUT).build(),
                "https://api.openai.com",
                HEALTH_TIMEOUT
        );
    }

    OpenAiLlmClient(
            String apiKey,
            String model,
            double temperature,
            int maxTokens,
            HttpClient healthHttpClient,
            String apiBaseUrl,
            Duration healthTimeout
    ) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.model = Objects.requireNonNull(model, "model");
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.healthHttpClient = Objects.requireNonNull(healthHttpClient, "healthHttpClient");
        this.apiBaseUrl = Objects.requireNonNull(apiBaseUrl, "apiBaseUrl");
        this.healthTimeout = Objects.requireNonNull(healthTimeout, "healthTimeout");
    }

    @Override
    public String generateRaw(LlmRequest request) {
        throw new LlmException("OpenAI client is not implemented yet.");
    }

    @Override
    public HealthCheckResult healthCheck() {
        long startNanos = System.nanoTime();
        URI uri;
        try {
            uri = URI.create(stripTrailingSlash(apiBaseUrl) + "/v1/models");
        } catch (RuntimeException ex) {
            return failed("Invalid base URL.", "INVALID_BASE_URL", startNanos, ex.getMessage());
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(healthTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<Void> response;
        try {
            response = healthHttpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (HttpTimeoutException ex) {
            return failed("Timeout while connecting to OpenAI.", "TIMEOUT", startNanos, ex.getMessage());
        } catch (IOException ex) {
            if (ex instanceof ConnectException || ex instanceof UnknownHostException) {
                return failed("Cannot connect to OpenAI.", "NETWORK_ERROR", startNanos, ex.getMessage());
            }
            return failed("Network error while connecting to OpenAI.", "NETWORK_ERROR", startNanos, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed("Health check interrupted.", "INTERRUPTED", startNanos, ex.getMessage());
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return HealthCheckResult.ok(PROVIDER_ID, "Connection successful.", elapsedMs(startNanos));
        }
        if (status == 401 || status == 403) {
            return failed("Authentication failed.", String.valueOf(status), startNanos, null);
        }
        if (status == 404) {
            return failed("Invalid OpenAI endpoint.", String.valueOf(status), startNanos, null);
        }
        return failed("Provider returned an unexpected response.", String.valueOf(status), startNanos, null);
    }

    public String model() {
        return model;
    }

    public double temperature() {
        return temperature;
    }

    public int maxTokens() {
        return maxTokens;
    }

    private HealthCheckResult failed(String userMessage, String technicalCode, long startNanos, String details) {
        String sanitized = SecretSanitizer.sanitize(details);
        String message = sanitized.isEmpty() ? userMessage : userMessage + " " + sanitized;
        return HealthCheckResult.failed(PROVIDER_ID, message, technicalCode, elapsedMs(startNanos));
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
