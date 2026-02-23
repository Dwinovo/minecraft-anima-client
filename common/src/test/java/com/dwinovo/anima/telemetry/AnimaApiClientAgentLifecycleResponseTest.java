package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.AgentLifecycleResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimaApiClientAgentLifecycleResponseTest {

    @Test
    void parsesCreatedAgentLifecycleResponse() {
        String body = """
            {
              "code": 0,
              "message": "agent created",
              "data": {
                "status": "created",
                "thread_id": "s_world:agent_1",
                "lifecycle_status": "ACTIVE",
                "version": 1
              }
            }
            """;

        AgentLifecycleResponse.AgentLifecycleData data = AnimaApiClient.parseAgentLifecycleData(201, body);

        assertNotNull(data);
        assertEquals("created", data.status());
        assertEquals("s_world:agent_1", data.thread_id());
        assertEquals("ACTIVE", data.lifecycle_status());
        assertEquals(1, data.version());
    }

    @Test
    void parsesDeactivatedAgentLifecycleResponse() {
        String body = """
            {
              "code": 0,
              "message": "agent deactivated",
              "data": {
                "status": "deactivated",
                "thread_id": "s_world:agent_1",
                "lifecycle_status": "DRAINING",
                "version": 2
              }
            }
            """;

        AgentLifecycleResponse.AgentLifecycleData data = AnimaApiClient.parseAgentLifecycleData(200, body);

        assertNotNull(data);
        assertEquals("deactivated", data.status());
        assertEquals("DRAINING", data.lifecycle_status());
        assertEquals(2, data.version());
    }

    @Test
    void rejectsInvalidAgentLifecycleResponse() {
        String body = """
            {
              "code": 500,
              "message": "internal error"
            }
            """;

        AgentLifecycleResponse.AgentLifecycleData data = AnimaApiClient.parseAgentLifecycleData(500, body);

        assertNull(data);
    }
}
