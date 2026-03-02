package io.testsmith.plugin.agent;

import io.testsmith.plugin.ui.model.AgentUiState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentProgressTest {
    @Test
    void roundsCoverageToSingleDecimal() {
        AgentProgress progress = new AgentProgress(
                AgentUiState.RUNNING,
                1,
                10,
                83.456,
                80.049,
                "com.example.Service",
                "msg",
                1L,
                2L
        );

        assertEquals(83.5, progress.currentCoverage());
        assertEquals(80.0, progress.targetCoverage());
    }

    @Test
    void clampsCoverageToRange0To100() {
        AgentProgress progress = new AgentProgress(
                AgentUiState.RUNNING,
                1,
                10,
                -4.0,
                178.2,
                null,
                "msg",
                1L,
                2L
        );
        assertEquals(0.0, progress.currentCoverage());
        assertEquals(100.0, progress.targetCoverage());
    }

    @Test
    void iterationRuleAllowsZeroOnlyForIdle() {
        assertThrows(IllegalArgumentException.class, () -> new AgentProgress(
                AgentUiState.RUNNING,
                0,
                10,
                1.0,
                80.0,
                null,
                "msg",
                1L,
                2L
        ));

        AgentProgress idle = new AgentProgress(
                AgentUiState.IDLE,
                0,
                0,
                0.0,
                80.0,
                null,
                "idle",
                1L,
                1L
        );
        assertEquals(0, idle.iteration());
    }

    @Test
    void nonIdleRequiresMaxIterationsAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> new AgentProgress(
                AgentUiState.RUNNING,
                1,
                0,
                10.0,
                80.0,
                null,
                "msg",
                1L,
                2L
        ));
    }
}
