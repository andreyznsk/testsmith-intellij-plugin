package io.testsmith.plugin.llm.ollama;

import io.testsmith.plugin.llm.api.LlmProtocolException;
import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.api.LlmTransportException;
import io.testsmith.plugin.llm.internal.JsonCodec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

final class HttpOllamaApi implements OllamaApi {
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;

    HttpOllamaApi(String baseUrl, String model, HttpClient httpClient) {
        OllamaLlmClient.validateConfig(baseUrl, model);
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.model = model;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public String generate(String prompt, LlmRequest request) {
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
            throw new LlmTransportException("Failed to call Ollama endpoint", ex);
        }

        if (httpResponse.statusCode() != 200) {
            throw new LlmTransportException(
                    "Ollama returned non-200 status: " + httpResponse.statusCode() + " body=" + httpResponse.body()
            );
        }

        return extractResponseText(httpResponse.body());
    }

    private String extractResponseText(String rawResponseBody) {
        Object parsed = JsonCodec.parse(rawResponseBody);
        if (!(parsed instanceof Map<?, ?> rawMap)) {
            throw new LlmProtocolException("Ollama response must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
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
}
