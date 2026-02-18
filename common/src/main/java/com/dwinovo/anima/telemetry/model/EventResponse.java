package com.dwinovo.anima.telemetry.model;

public record EventResponse(
    int code,
    String message,
    EventDataResponse data
) {
    public record EventDataResponse(
        String session_id
    ) {}
}
