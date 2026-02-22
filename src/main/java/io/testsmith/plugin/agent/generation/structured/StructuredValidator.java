package io.testsmith.plugin.agent.generation.structured;

public interface StructuredValidator {
    ValidationResult validate(StructuredTest test, GenerationContext context);
}
