package io.testsmith.plugin.llm.ollama;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.api.LlmProtocolException;
import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.api.LlmTransportException;
import io.testsmith.plugin.llm.api.LlmTuning;
import io.testsmith.plugin.llm.api.TestFramework;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaLlmClientTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsRequestToHttpPayload() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        startServer(exchange -> {
            bodyRef.set(readBody(exchange));
            String response = "{\"response\":\"{\\\"testClassFqcn\\\":\\\"com.example.ServiceTest\\\",\\\"suggestedFilePath\\\":\\\"src/test/java/com/example/ServiceTest.java\\\",\\\"testFramework\\\":\\\"JUNIT5\\\",\\\"javaSource\\\":\\\"package com.example;\\\\npublic class ServiceTest {}\\\",\\\"notes\\\":[]}\"}";
            writeResponse(exchange, 200, response);
        });

        OllamaLlmClient client = new OllamaLlmClient(baseUrl(), "qwen2.5-coder:7b", HttpClient.newHttpClient());

        var response = client.generateTest(request(Duration.ofSeconds(5), new LlmTuning(0.3, 0.8, 1.2)));

        assertEquals("com.example.ServiceTest", response.testClassFqcn());
        String body = bodyRef.get();
        assertTrue(body.contains("\"model\":\"qwen2.5-coder:7b\""));
        assertTrue(body.contains("\"stream\":false"));
        assertTrue(body.contains("\"temperature\":0.3"));
        assertTrue(body.contains("\"top_p\":0.8"));
        assertTrue(body.contains("\"repeat_penalty\":1.2"));
    }

    @Test
    void handlesNon200AsTransportException() throws Exception {
        startServer(exchange -> writeResponse(exchange, 500, "boom"));

        OllamaLlmClient client = new OllamaLlmClient(baseUrl(), "qwen2.5-coder:7b", HttpClient.newHttpClient());

        assertThrows(LlmTransportException.class, () -> client.generateTest(request(Duration.ofSeconds(5), LlmTuning.defaults())));
    }

    @Test
    void handlesTimeoutAsTransportException() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            writeResponse(exchange, 200, "{\"response\":\"{}\"}");
        });

        OllamaLlmClient client = new OllamaLlmClient(baseUrl(), "qwen2.5-coder:7b", HttpClient.newHttpClient());

        assertThrows(LlmTransportException.class, () -> client.generateTest(request(Duration.ofMillis(100), LlmTuning.defaults())));
    }

    @Test
    void handlesInvalidJsonAsProtocolException() throws Exception {
        startServer(exchange -> writeResponse(exchange, 200, "{\"response\":\"not-json\"}"));

        OllamaLlmClient client = new OllamaLlmClient(baseUrl(), "qwen2.5-coder:7b", HttpClient.newHttpClient());

        assertThrows(LlmProtocolException.class, () -> client.generateTest(request(Duration.ofSeconds(5), LlmTuning.defaults())));
    }

    private void startServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/generate", handler);
        server.start();
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static LlmRequest request(Duration timeout, LlmTuning tuning) {
        return new LlmRequest(
                "com.example.Service",
                "package com.example; public class Service {}",
                List.of(),
                TestFramework.JUNIT5,
                "",
                GenerationMode.GENERATE,
                "",
                tuning,
                timeout,
                Map.of("traceId", "t-1")
        );
    }
}
