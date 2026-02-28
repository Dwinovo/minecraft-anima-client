package com.dwinovo.anima.telemetry.event.core;

import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventVerb;
import com.dwinovo.anima.telemetry.model.details.EventDetails;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventRequestAssemblerTest {

    @Test
    void buildsRequestFromTypedDetails() {
        EventRequest.EntityRequest subject = new EventRequest.EntityRequest("s", "minecraft:player", "Steve", null, null);
        EventRequest.EntityRequest object = new EventRequest.EntityRequest("o", "minecraft:zombie", "Zombie", null, null);
        EventDetails details = () -> Map.of("key", "value");

        EventRequest request = EventRequestAssembler.build(
            "session-1",
            99L,
            subject,
            EventVerb.ATTACKED,
            details,
            object
        );

        assertEquals("session-1", request.session_id());
        assertEquals(99L, request.world_time());
        assertEquals("ATTACKED", request.action().verb());
        assertEquals("value", request.action().details().get("key"));
        assertEquals(subject, request.subject());
        assertEquals(object, request.object());
        assertNotNull(request.timestamp());
    }
}

