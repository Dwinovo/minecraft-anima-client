package com.dwinovo.anima.telemetry.model;

import java.util.Collections;
import java.util.Map;

public record EventRequest(
    String session_id,
    long world_time,
    String timestamp,
    EntityRequest subject,
    ActionRequest action,
    EntityRequest object
) {
    public record LocationRequest(
        String dimension,
        String biome,
        double[] coordinates
    ) {}

    // 纯粹的生存指标状态，无冗余字段
    public record EntityStateRequest(
        float health,
        float max_health
    ) {}

    public record EntityRequest(
        String entity_id,
        String entity_type,
        String name,
        LocationRequest location,
        EntityStateRequest state
    ) {}

    public record ActionRequest(
        String verb,
        Map<String, Object> details
    ) {
        public ActionRequest {
            details = details == null ? Collections.emptyMap() : details;
        }
    }
}