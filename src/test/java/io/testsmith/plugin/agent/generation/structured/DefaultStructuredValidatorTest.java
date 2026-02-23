package io.testsmith.plugin.agent.generation.structured;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultStructuredValidatorTest {
    private final DefaultStructuredValidator validator = new DefaultStructuredValidator();

    @Test
    void validatesHappyPath() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of("org.junit.jupiter.api.Test"),
                "package com.example;\npublic class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                List.of()
        );

        ValidationResult result = validator.validate(test, context);

        assertTrue(result.valid());
    }

    @Test
    void rejectsWrongVersion() {
        StructuredTest test = new StructuredTest(
                "2.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "public class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(StructuredAction.GENERATE_TEST, "com.example.Service", List.of());

        ValidationResult result = validator.validate(test, context);

        assertFalse(result.valid());
        assertEquals(ValidationErrorType.SCHEMA_INVALID, result.errorType());
    }

    @Test
    void rejectsClassNameMismatch() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "public class AnotherTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(StructuredAction.GENERATE_TEST, "com.example.Service", List.of());

        ValidationResult result = validator.validate(test, context);

        assertFalse(result.valid());
        assertEquals(ValidationErrorType.SEMANTIC_INVALID, result.errorType());
    }

    @Test
    void rejectsProhibitedAnnotationWhenUndiscovered() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "@SpringBootTest\npublic class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(StructuredAction.GENERATE_TEST, "com.example.Service", List.of());

        ValidationResult result = validator.validate(test, context);

        assertFalse(result.valid());
        assertEquals(ValidationErrorType.SEMANTIC_INVALID, result.errorType());
    }

    @Test
    void rejectsInfrastructureRequirement() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "public class ServiceTest {}",
                List.of(),
                true,
                null,
                null
        );
        GenerationContext context = new GenerationContext(StructuredAction.GENERATE_TEST, "com.example.Service", List.of());

        ValidationResult result = validator.validate(test, context);

        assertFalse(result.valid());
        assertEquals(ValidationErrorType.INFRASTRUCTURE_REQUIRED, result.errorType());
    }

    @Test
    void rejectsWrapperProseDeterministically() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "Generated test output:\npublic class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(StructuredAction.GENERATE_TEST, "com.example.Service", List.of());

        ValidationResult result = validator.validate(test, context);

        assertFalse(result.valid());
        assertEquals(ValidationErrorType.SEMANTIC_INVALID, result.errorType());
    }

    @Test
    void allowsJavaCodeAfterLeadingComments() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "/* generated by model */\n// test class\npackage com.example;\npublic class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(StructuredAction.GENERATE_TEST, "com.example.Service", List.of());

        ValidationResult result = validator.validate(test, context);

        assertTrue(result.valid());
    }

    @Test
    void allowsAnnotationStartWhenDiscovered() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "@MyCustomTest\npublic class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                List.of("@MyCustomTest")
        );

        ValidationResult result = validator.validate(test, context);

        assertTrue(result.valid());
    }

    @Test
    void rejectsWrapperProseBetweenAnnotationAndCode() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "@MyCustomTest\nHere is the test:\nclass ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                List.of("@MyCustomTest")
        );

        ValidationResult result = validator.validate(test, context);

        assertFalse(result.valid());
        assertEquals(ValidationErrorType.SEMANTIC_INVALID, result.errorType());
    }

    @Test
    void allowsCommentLineBetweenAnnotationAndCode() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "@MyCustomTest\n// comment\nclass ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                List.of("@MyCustomTest")
        );

        ValidationResult result = validator.validate(test, context);

        assertTrue(result.valid());
    }

    @Test
    void allowsBlockCommentBetweenAnnotationAndCode() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "@MyCustomTest\n/* comment */\nclass ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                List.of("@MyCustomTest")
        );

        ValidationResult result = validator.validate(test, context);

        assertTrue(result.valid());
    }

    @Test
    void allowsFinalClassAtStart() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "final class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(StructuredAction.GENERATE_TEST, "com.example.Service", List.of());

        ValidationResult result = validator.validate(test, context);

        assertTrue(result.valid());
    }

    @Test
    void allowsAbstractClassAtStart() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "abstract class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(StructuredAction.GENERATE_TEST, "com.example.Service", List.of());

        ValidationResult result = validator.validate(test, context);

        assertTrue(result.valid());
    }

    @Test
    void allowsAnnotationStartWithoutRequiringDiscovery() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "@ExtendWith(MockitoExtension.class)\nclass ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(StructuredAction.GENERATE_TEST, "com.example.Service", List.of());

        ValidationResult result = validator.validate(test, context);

        assertTrue(result.valid());
    }

    @Test
    void rejectsWrapperProseBeforeCode() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "Here is the test:\nfinal class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(StructuredAction.GENERATE_TEST, "com.example.Service", List.of());

        ValidationResult result = validator.validate(test, context);

        assertFalse(result.valid());
        assertEquals(ValidationErrorType.SEMANTIC_INVALID, result.errorType());
    }

    @Test
    void validatesRepairContractVersionAndAction() {
        StructuredTest test = new StructuredTest(
                "1.1",
                StructuredAction.REPAIR_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "public class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(
                StructuredAction.REPAIR_TEST,
                "com.example.Service",
                "1.1",
                List.of()
        );

        ValidationResult result = validator.validate(test, context);

        assertTrue(result.valid());
    }

    @Test
    void rejectsRepairWhenVersionDoesNotMatchContext() {
        StructuredTest test = new StructuredTest(
                "1.0",
                StructuredAction.REPAIR_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "public class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(
                StructuredAction.REPAIR_TEST,
                "com.example.Service",
                "1.1",
                List.of()
        );

        ValidationResult result = validator.validate(test, context);

        assertFalse(result.valid());
        assertEquals(ValidationErrorType.SCHEMA_INVALID, result.errorType());
    }

    @Test
    void rejectsRepairWhenActionDoesNotMatchRepairContract() {
        StructuredTest test = new StructuredTest(
                "1.1",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of(),
                "public class ServiceTest {}",
                List.of(),
                false,
                null,
                null
        );
        GenerationContext context = new GenerationContext(
                StructuredAction.REPAIR_TEST,
                "com.example.Service",
                "1.1",
                List.of()
        );

        ValidationResult result = validator.validate(test, context);

        assertFalse(result.valid());
        assertEquals(ValidationErrorType.SEMANTIC_INVALID, result.errorType());
        assertTrue(result.message().contains("action must equal REPAIR_TEST"));
    }
}
