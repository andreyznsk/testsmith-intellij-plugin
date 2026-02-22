package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.*;
import io.testsmith.plugin.llm.api.*;
import io.testsmith.plugin.testrunner.model.TestExecutionPhase;
import io.testsmith.plugin.testrunner.model.TestExecutionResult;
import io.testsmith.plugin.testrunner.model.TestExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmTestRepairAgentTest {

    @Test
    void buildsDeterministicRepairRequestAndAcceptsValidRepair() {
        RecordingClient client = new RecordingClient(List.of(
                """
                        {
                          "version": "1.1",
                          "action": "REPAIR_TEST",
                          "targetClass": "com.example.Service",
                          "testClassName": "ServiceTest",
                          "imports": ["org.junit.jupiter.api.Test"],
                          "code": "package com.example;\\npublic class ServiceTest {}",
                          "assumptions": [],
                          "requiresInfrastructure": false
                        }
                        """
        ));

        StructuredGenerationGateway gateway = new StructuredGenerationGateway(
                client,
                new StrictStructuredResponseParser(),
                new DefaultStructuredValidator(),
                new StructuredRetryPolicy(0)
        );

        LlmTestRepairAgent agent = new LlmTestRepairAgent(gateway);
        RepairResult result = agent.attemptRepair(generatedTest(), compilationFailure(), baseContext());

        assertEquals(RepairOutcome.REPAIRED, result.outcome());
        assertEquals("1.1", result.repairedTest().version());
        assertEquals(StructuredAction.REPAIR_TEST, result.repairedTest().action());

        LlmRequest request = client.requests.getFirst();
        assertEquals(GenerationMode.FIX, request.mode());
        assertEquals(0.1, request.tuning().temperature());
        assertTrue(request.failureContext().contains("FailureClassification:"));
        assertTrue(request.failureContext().contains("OriginalStructuredTest:"));
    }

    @Test
    void hardAbortsWhenRepairRequestsInfrastructure() {
        RecordingClient client = new RecordingClient(List.of(
                """
                        {
                          "version": "1.1",
                          "action": "REPAIR_TEST",
                          "targetClass": "com.example.Service",
                          "testClassName": "ServiceTest",
                          "imports": ["org.junit.jupiter.api.Test"],
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
                new StructuredRetryPolicy(0)
        );

        LlmTestRepairAgent agent = new LlmTestRepairAgent(gateway);
        RepairResult result = agent.attemptRepair(generatedTest(), compilationFailure(), baseContext());

        assertEquals(RepairOutcome.HARD_ABORT, result.outcome());
        assertTrue(result.message().contains("infrastructure"));
    }

    private static RepairContext baseContext() {
        return new RepairContext(
                "com.example.Service",
                "package com.example; public class Service {}",
                List.of(),
                TestFramework.JUNIT5,
                "",
                List.of(),
                Duration.ofSeconds(5),
                Map.of(),
                RepairMode.AUTONOMOUS,
                1,
                generatedTest()
        );
    }

    private static StructuredTest generatedTest() {
        return new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of("org.junit.jupiter.api.Test"),
                "package com.example;\npublic class ServiceTest {}",
                List.of(),
                false,
                0.8,
                Map.of()
        );
    }

    private static TestExecutionResult compilationFailure() {
        return new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.COMPILATION_FAILED,
                null,
                null,
                "cannot find symbol",
                null,
                "",
                "cannot find symbol\nimport com.example.Missing",
                Duration.ofMillis(20)
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
