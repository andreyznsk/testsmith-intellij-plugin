package io.testsmith.plugin.llm.openai;

import com.sun.net.httpserver.HttpServer;
import io.testsmith.plugin.llm.api.HealthCheckResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiLlmClientHealthCheckTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsTimeoutToFailedResult() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(700L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        OpenAiLlmClient client = new OpenAiLlmClient(
                "sk-secret",
                "gpt-4o-mini",
                0.1,
                256,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build(),
                baseUrl(),
                Duration.ofMillis(150)
        );

        HealthCheckResult result = client.healthCheck();

        assertEquals(HealthCheckResult.Status.FAILED, result.status());
        assertEquals("TIMEOUT", result.technicalCode());
        assertTrue(result.userMessage().toLowerCase().contains("timeout"));
    }

    @Test
    void mapsAuthErrorsToAuthenticationFailed() throws Exception {
        startServer(exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        OpenAiLlmClient client = new OpenAiLlmClient(
                "sk-secret",
                "gpt-4o-mini",
                0.1,
                256,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build(),
                baseUrl(),
                Duration.ofMillis(300)
        );

        HealthCheckResult result = client.healthCheck();

        assertEquals(HealthCheckResult.Status.FAILED, result.status());
        assertEquals("401", result.technicalCode());
        assertTrue(result.userMessage().toLowerCase().contains("authentication"));
    }

    @Test
    void mapsConnectionRefusedToCannotConnect() throws Exception {
        int unusedPort = findUnusedPort();
        OpenAiLlmClient client = new OpenAiLlmClient(
                "sk-secret",
                "gpt-4o-mini",
                0.1,
                256,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build(),
                "http://127.0.0.1:" + unusedPort,
                Duration.ofMillis(300)
        );

        HealthCheckResult result = client.healthCheck();

        assertEquals(HealthCheckResult.Status.FAILED, result.status());
        assertEquals("NETWORK_ERROR", result.technicalCode());
        assertTrue(result.userMessage().toLowerCase().contains("cannot connect"));
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/models", handler);
        server.start();
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static int findUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
