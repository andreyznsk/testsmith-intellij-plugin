package io.testsmith.plugin.agent;

import io.testsmith.plugin.ui.model.AgentUiState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
