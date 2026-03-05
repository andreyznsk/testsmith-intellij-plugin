package io.testsmith.plugin.llm.gigachat;

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

public final class GigaChatLlmClient implements LlmClient {
    private static final String PROVIDER_ID = "GIGACHAT";
    private static final Duration HEALTH_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);

    private final String apiKey;
    private final String model;
    private final String endpoint;
    private final HttpClient healthHttpClient;
    private final Duration healthTimeout;

    public GigaChatLlmClient(String apiKey, String model, String endpoint) {
        this(
                apiKey,
                model,
                endpoint,
                HttpClient.newBuilder().connectTimeout(HEALTH_CONNECT_TIMEOUT).build(),
                HEALTH_TIMEOUT
        );
    }

    GigaChatLlmClient(
            String apiKey,
            String model,
            String endpoint,
            HttpClient healthHttpClient,
            Duration healthTimeout
    ) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.model = Objects.requireNonNull(model, "model");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.healthHttpClient = Objects.requireNonNull(healthHttpClient, "healthHttpClient");
        this.healthTimeout = Objects.requireNonNull(healthTimeout, "healthTimeout");
    }

    @Override
    public String generateRaw(LlmRequest request) {
        throw new LlmException("GigaChat client is not implemented yet.");
    }

    @Override
    public HealthCheckResult healthCheck() {
        long startNanos = System.nanoTime();
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (RuntimeException ex) {
            return failed("Invalid endpoint URL.", "INVALID_BASE_URL", startNanos, ex.getMessage());
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
            return failed("Timeout while connecting to GigaChat.", "TIMEOUT", startNanos, ex.getMessage());
        } catch (IOException ex) {
            if (ex instanceof ConnectException || ex instanceof UnknownHostException) {
                return failed("Cannot connect to GigaChat.", "NETWORK_ERROR", startNanos, ex.getMessage());
            }
            return failed("Network error while connecting to GigaChat.", "NETWORK_ERROR", startNanos, ex.getMessage());
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
        if (status == 404 || status == 405 || status == 400) {
            return failed("Invalid GigaChat endpoint.", String.valueOf(status), startNanos, null);
        }
        return failed("Provider returned an unexpected response.", String.valueOf(status), startNanos, null);
    }

    public String model() {
        return model;
    }

    public String endpoint() {
        return endpoint;
    }

    private HealthCheckResult failed(String userMessage, String technicalCode, long startNanos, String details) {
        String sanitized = SecretSanitizer.sanitize(details);
        String message = sanitized.isEmpty() ? userMessage : userMessage + " " + sanitized;
        return HealthCheckResult.failed(PROVIDER_ID, message, technicalCode, elapsedMs(startNanos));
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
