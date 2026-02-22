package io.testsmith.plugin.agent.generation.structured;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrictStructuredResponseParserTest {
    private final StrictStructuredResponseParser parser = new StrictStructuredResponseParser();

    @Test
    void parsesValidResponse() {
        String payload = """
                {
                  "version": "1.0",
                  "action": "GENERATE_TEST",
                  "targetClass": "com.example.Service",
                  "testClassName": "ServiceTest",
                  "imports": ["org.junit.jupiter.api.Test"],
                  "code": "package com.example;\\npublic class ServiceTest {}",
                  "assumptions": [],
                  "requiresInfrastructure": false,
                  "confidence": 0.92,
                  "metadata": {"provider": "ollama"}
                }
                """;

        StructuredTest result = parser.parse(payload);

        assertEquals("1.0", result.version());
        assertEquals(StructuredAction.GENERATE_TEST, result.action());
        assertEquals("com.example.Service", result.targetClass());
        assertEquals("ServiceTest", result.testClassName());
        assertEquals(1, result.imports().size());
        assertEquals(false, result.requiresInfrastructure());
        assertEquals(0.92, result.confidence());
        assertEquals("ollama", result.metadata().get("provider"));
    }

    @Test
    void rejectsWrapperText() {
        StructuredResponseException ex = assertThrows(
                StructuredResponseException.class,
                () -> parser.parse("Here is output: {\"version\":\"1.0\"}")
        );

        assertEquals(ValidationErrorType.STRUCTURE_INVALID, ex.errorType());
    }

    @Test
    void rejectsMissingRequiredFields() {
        String payload = """
                {
                  "version": "1.0",
                  "action": "GENERATE_TEST",
                  "targetClass": "com.example.Service",
                  "testClassName": "ServiceTest",
                  "imports": [],
                  "code": "public class ServiceTest {}",
                  "requiresInfrastructure": false
                }
                """;

        StructuredResponseException ex = assertThrows(StructuredResponseException.class, () -> parser.parse(payload));
        assertEquals(ValidationErrorType.SCHEMA_INVALID, ex.errorType());
    }

    @Test
    void rejectsUnknownFields() {
        String payload = """
                {
                  "version": "1.0",
                  "action": "GENERATE_TEST",
                  "targetClass": "com.example.Service",
                  "testClassName": "ServiceTest",
                  "imports": [],
                  "code": "public class ServiceTest {}",
                  "assumptions": [],
                  "requiresInfrastructure": false,
                  "unexpected": true
                }
                """;

        StructuredResponseException ex = assertThrows(StructuredResponseException.class, () -> parser.parse(payload));
        assertEquals(ValidationErrorType.SCHEMA_INVALID, ex.errorType());
    }

    @Test
    void parsesRepairContractV11() {
        String payload = """
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
                """;

        StructuredTest result = parser.parse(payload);

        assertEquals("1.1", result.version());
        assertEquals(StructuredAction.REPAIR_TEST, result.action());
    }
}
