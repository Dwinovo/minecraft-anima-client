package com.dwinovo.anima.telemetry.model;

public record AgentDeactivateRequest(
    String session_id,
    String entity_uuid,
    int expected_version,
    boolean graceful
) {}
