package io.testsmith.plugin.llm.ollama;

import com.intellij.openapi.diagnostic.Logger;
import io.testsmith.plugin.llm.api.HealthCheckResult;
import io.testsmith.plugin.llm.api.LlmProtocolException;
import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.api.LlmTransportException;
import io.testsmith.plugin.llm.internal.JsonCodec;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class HttpOllamaApi implements OllamaApi {
    private static final Logger LOG = Logger.getInstance(HttpOllamaApi.class);
    private static final String PROVIDER_ID = "OLLAMA";
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ERROR_BODY_CHARS = 4096;
    private static final int DEBUG_BODY_CHARS = 512;

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final HttpClient healthHttpClient;

    HttpOllamaApi(String baseUrl, String model, HttpClient httpClient) {
        OllamaLlmClient.validateConfig(baseUrl, model);
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.model = model;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.healthHttpClient = HttpClient.newBuilder().connectTimeout(HEALTH_TIMEOUT).build();
    }

    @Override
    public String generate(String prompt, LlmRequest request) {
        String uri = SecretSanitizer.sanitize(baseUrl + "/api/generate");
        LOG.debug("Ollama generate request: method=POST uri=" + uri + " timeoutMs=" + request.timeout().toMillis());
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(request.timeout())
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(prompt, request)))
                .build();

        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.debug("Ollama generate transport error: uri="
                    + uri
                    + " error="
                    + SecretSanitizer.sanitize(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
            throw new LlmTransportException("Failed to call Ollama endpoint", ex);
        }
        LOG.debug("Ollama generate response: status="
                + httpResponse.statusCode()
                + " uri="
                + uri
                + " bodyPreview="
                + SecretSanitizer.sanitize(truncate(httpResponse.body(), DEBUG_BODY_CHARS)));

        if (httpResponse.statusCode() != 200) {
            throw new LlmTransportException(
                    "Ollama returned non-200 status: " + httpResponse.statusCode() + " body="
                            + SecretSanitizer.sanitize(truncate(httpResponse.body(), MAX_ERROR_BODY_CHARS))
            );
        }

        return extractResponseText(httpResponse.body());
    }

    @Override
    public HealthCheckResult healthCheck() {
        long startNanos = System.nanoTime();
        String safeUri = SecretSanitizer.sanitize(baseUrl + "/api/version");
        LOG.debug("Ollama health check request: method=GET uri=" + safeUri + " timeoutMs=" + HEALTH_TIMEOUT.toMillis());
        URI requestUri;
        try {
            requestUri = URI.create(baseUrl + "/api/version");
        } catch (RuntimeException ex) {
            return failed("Invalid base URL.", "INVALID_BASE_URL", startNanos, ex.getMessage());
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(requestUri)
                .timeout(HEALTH_TIMEOUT)
                .GET()
                .build();

        HttpResponse<Void> response;
        try {
            response = healthHttpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (HttpTimeoutException ex) {
            LOG.debug("Ollama health check timeout: uri=" + SecretSanitizer.sanitize(requestUri.toString()));
            return failed("Timeout while connecting to Ollama.", "TIMEOUT", startNanos, ex.getMessage());
        } catch (IOException ex) {
            LOG.debug("Ollama health check IO error: uri="
                    + SecretSanitizer.sanitize(requestUri.toString())
                    + " error="
                    + SecretSanitizer.sanitize(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
            if (ex instanceof ConnectException || ex instanceof UnknownHostException) {
                return failed("Cannot connect to Ollama.", "NETWORK_ERROR", startNanos, ex.getMessage());
            }
            return failed("Network error while connecting to Ollama.", "NETWORK_ERROR", startNanos, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.debug("Ollama health check interrupted: uri=" + SecretSanitizer.sanitize(requestUri.toString()));
            return failed("Health check interrupted.", "INTERRUPTED", startNanos, ex.getMessage());
        }
        LOG.debug("Ollama health check response: status="
                + response.statusCode()
                + " uri="
                + SecretSanitizer.sanitize(requestUri.toString()));

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return HealthCheckResult.ok(PROVIDER_ID, "Connection successful.", elapsedMs(startNanos));
        }
        if (status == 401 || status == 403) {
            return failed("Authentication failed.", String.valueOf(status), startNanos, null);
        }
        if (status == 404) {
            return failed("Invalid Ollama URL.", String.valueOf(status), startNanos, null);
        }
        return failed("Provider returned an unexpected response.", String.valueOf(status), startNanos, null);
    }

    private String extractResponseText(String rawResponseBody) {
        Object parsed = JsonCodec.parse(rawResponseBody);
        if (!(parsed instanceof Map<?, ?> rawMap)) {
            throw new LlmProtocolException("Ollama response must be a JSON object");
        }
        Map<String, Object> map = toStringObjectMap(rawMap);
        Object value = map.get("response");
        if (!(value instanceof String responseText)) {
            throw new LlmProtocolException("Ollama response JSON missing string field 'response'");
        }
        return responseText;
    }

    private String buildPayload(String prompt, LlmRequest request) {
        return "{" +
                "\"model\":" + JsonCodec.toJsonString(model) + "," +
                "\"prompt\":" + JsonCodec.toJsonString(prompt) + "," +
                "\"stream\":false," +
                "\"options\":{" +
                "\"temperature\":" + request.tuning().temperature() + "," +
                "\"top_p\":" + request.tuning().topP() + "," +
                "\"repeat_penalty\":" + request.tuning().repeatPenalty() +
                "}" +
                "}";
    }

    private static String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String keyString)) {
                throw new LlmProtocolException("Ollama response contains a non-string key");
            }
            result.put(keyString, entry.getValue());
        }
        return result;
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...(truncated)";
    }

    private static HealthCheckResult failed(String userMessage, String technicalCode, long startNanos, String details) {
        String sanitized = SecretSanitizer.sanitize(details);
        String message = sanitized.isEmpty() ? userMessage : userMessage + " " + sanitized;
        return HealthCheckResult.failed(PROVIDER_ID, message, technicalCode, elapsedMs(startNanos));
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
