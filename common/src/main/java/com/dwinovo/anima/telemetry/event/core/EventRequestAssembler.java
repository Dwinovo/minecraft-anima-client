package com.dwinovo.anima.telemetry.event.core;

import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventVerb;
import com.dwinovo.anima.telemetry.model.details.EventDetails;

import java.time.Instant;

public final class EventRequestAssembler {

    private EventRequestAssembler() {}

    public static <T extends EventDetails> EventRequest build(
        String sessionId,
        long worldTime,
        EventRequest.EntityRequest subject,
        EventVerb verb,
        T details,
        EventRequest.EntityRequest object
    ) {
        return new EventRequest(
            sessionId,
            worldTime,
            Instant.now().toString(),
            subject,
            new EventRequest.ActionRequest(verb.value(), details == null ? null : details.toMap()),
            object
        );
    }
}

