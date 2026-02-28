package com.dwinovo.anima.telemetry.api;

import com.dwinovo.anima.telemetry.model.TickResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimaApiClientTickResponseTest {

    @Test
    void parsesLegacyTickResultResponse() {
        String body = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "session_id": "s_001",
                "total_agents": 2,
                "succeeded": 1,
                "failed": 1,
                "results": []
              }
            }
            """;

        TickResponse.TickDataResponse data = AnimaApiClient.parseTickData(200, body, "fallback");

        assertNotNull(data);
        assertEquals("s_001", data.session_id());
        assertEquals(2, data.total_agents());
        assertEquals(1, data.succeeded());
        assertEquals(1, data.failed());
    }

    @Test
    void acceptsApiResponseWrappedAsyncTickAck() {
        String body = """
            {
              "code": 0,
              "message": "tick accepted",
              "data": {
                "status": "accepted",
                "session_id": "s_002"
              }
            }
            """;

        TickResponse.TickDataResponse data = AnimaApiClient.parseTickData(202, body, "fallback");

        assertNotNull(data);
        assertEquals("s_002", data.session_id());
        assertEquals(0, data.total_agents());
        assertEquals(0, data.succeeded());
        assertEquals(0, data.failed());
        assertEquals(0, data.results().size());
    }

    @Test
    void acceptsBareAsyncTickAckAndFallsBackToRequestSessionId() {
        String body = """
            {
              "status": "accepted"
            }
            """;

        TickResponse.TickDataResponse data = AnimaApiClient.parseTickData(200, body, "s_003");

        assertNotNull(data);
        assertEquals("s_003", data.session_id());
    }

    @Test
    void rejectsInvalidPayload() {
        String body = """
            {
              "code": 500,
              "message": "internal error"
            }
            """;

        TickResponse.TickDataResponse data = AnimaApiClient.parseTickData(500, body, "s_004");

        assertNull(data);
    }
}

