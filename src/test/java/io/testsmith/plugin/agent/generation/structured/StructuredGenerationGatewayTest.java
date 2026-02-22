package io.testsmith.plugin.agent.generation.structured;

import io.testsmith.plugin.llm.api.GenerationMode;
import io.testsmith.plugin.llm.api.LlmClient;
import io.testsmith.plugin.llm.api.LlmRequest;
import io.testsmith.plugin.llm.api.LlmTuning;
import io.testsmith.plugin.llm.api.TestFramework;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredGenerationGatewayTest {

    @Test
    void retriesOnStructureInvalidThenSucceeds() {
        RecordingClient client = new RecordingClient(List.of(
                "not-json",
                """
                {
                  "version": "1.0",
                  "action": "GENERATE_TEST",
                  "targetClass": "com.example.Service",
                  "testClassName": "ServiceTest",
                  "imports": [],
                  "code": "public class ServiceTest {}",
                  "assumptions": [],
                  "requiresInfrastructure": false
                }
                """
        ));

        StructuredGenerationGateway gateway = new StructuredGenerationGateway(
                client,
                new StrictStructuredResponseParser(),
                new DefaultStructuredValidator(),
                new StructuredRetryPolicy(2)
        );

        StructuredTest test = gateway.generateValidated(baseRequest(), context());

        assertEquals("ServiceTest", test.testClassName());
        assertEquals(2, client.requests.size());
        assertTrue(client.requests.get(1).projectTestPatternNotes().contains("[structured-retry]"));
    }

    @Test
    void doesNotRetryWhenInfrastructureIsRequired() {
        RecordingClient client = new RecordingClient(List.of(
                """
                {
                  "version": "1.0",
                  "action": "GENERATE_TEST",
                  "targetClass": "com.example.Service",
                  "testClassName": "ServiceTest",
                  "imports": [],
                  "code": "public class ServiceTest {}",
                  "assumptions": [],
                  "requiresInfrastructure": true
                }
                """
        ));

        StructuredGenerationGateway gateway = new StructuredGenerationGateway(
                client,
                new StrictStructuredResponseParser(),
                new DefaultStructuredValidator(),
                new StructuredRetryPolicy(2)
        );

        StructuredGenerationException ex = assertThrows(
                StructuredGenerationException.class,
                () -> gateway.generateValidated(baseRequest(), context())
        );

        assertEquals(ValidationErrorType.INFRASTRUCTURE_REQUIRED, ex.errorType());
        assertEquals(1, client.requests.size());
    }

    private static LlmRequest baseRequest() {
        return new LlmRequest(
                "com.example.Service",
                "package com.example; public class Service {}",
                List.of(),
                TestFramework.JUNIT5,
                "",
                GenerationMode.GENERATE,
                "",
                LlmTuning.defaults(),
                Duration.ofSeconds(5),
                Map.of("traceId", "iter3")
        );
    }

    private static GenerationContext context() {
        return new GenerationContext(
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                List.of()
        );
    }

    private static final class RecordingClient implements LlmClient {
        private final List<String> responses;
        private int index = 0;
        private final List<LlmRequest> requests = new ArrayList<>();

        private RecordingClient(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String generateRaw(LlmRequest request) {
            requests.add(request);
            if (index >= responses.size()) {
                throw new IllegalStateException("No more stub responses");
            }
            return responses.get(index++);
        }
    }
}
