package com.dwinovo.anima.telemetry.model;

public record AgentActivateRequest(
    String session_id,
    String entity_uuid,
    String entity_type,
    String profile
) {}
