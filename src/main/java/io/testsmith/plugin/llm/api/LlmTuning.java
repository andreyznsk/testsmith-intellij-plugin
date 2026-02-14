package io.testsmith.plugin.llm.api;

/**
 * Deterministic generation tuning.
 */
public record LlmTuning(
        double temperature,
        double topP,
        double repeatPenalty
) {
    public static final double DEFAULT_TEMPERATURE = 0.1;
    public static final double DEFAULT_TOP_P = 0.9;
    public static final double DEFAULT_REPEAT_PENALTY = 1.1;

    public LlmTuning {
        requireFinite(temperature, "temperature");
        requireFinite(topP, "topP");
        requireFinite(repeatPenalty, "repeatPenalty");
        if (topP <= 0.0 || topP > 1.0) {
            throw new IllegalArgumentException("topP must be in (0, 1]");
        }
        if (repeatPenalty <= 0.0) {
            throw new IllegalArgumentException("repeatPenalty must be > 0");
        }
    }

    public static LlmTuning defaults() {
        return new LlmTuning(DEFAULT_TEMPERATURE, DEFAULT_TOP_P, DEFAULT_REPEAT_PENALTY);
    }

    private static void requireFinite(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
