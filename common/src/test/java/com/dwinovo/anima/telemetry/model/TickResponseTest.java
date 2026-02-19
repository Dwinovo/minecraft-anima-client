package com.dwinovo.anima.telemetry.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TickResponseTest {

    @Test
    void defaultsResultsToEmptyListWhenNull() {
        TickResponse.TickDataResponse data = new TickResponse.TickDataResponse(
            "s_001",
            2,
            1,
            1,
            null
        );

        assertNotNull(data.results());
        assertEquals(0, data.results().size());
    }

    @Test
    void keepsProvidedResults() {
        TickResponse.TickAgentResultResponse result = new TickResponse.TickAgentResultResponse(
            "agent-a",
            true,
            "ok"
        );
        TickResponse.TickDataResponse data = new TickResponse.TickDataResponse(
            "s_001",
            1,
            1,
            0,
            List.of(result)
        );

        assertEquals(1, data.results().size());
        assertEquals("agent-a", data.results().getFirst().agent_uuid());
    }
}
