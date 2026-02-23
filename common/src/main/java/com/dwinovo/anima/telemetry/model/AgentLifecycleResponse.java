package com.dwinovo.anima.telemetry.model;

public record AgentLifecycleResponse(
    int code,
    String message,
    AgentLifecycleData data
) {
    public record AgentLifecycleData(
        String status,
        String thread_id,
        String lifecycle_status,
        Integer version
    ) {}
}
