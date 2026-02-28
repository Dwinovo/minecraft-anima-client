package com.dwinovo.anima.telemetry.model.details;

import java.util.LinkedHashMap;
import java.util.Map;

public record AttackedDetails(
    String attack_tool,
    float attack_damage,
    float victim_health_before,
    float victim_health_after
) implements EventDetails {
    public Map<String, Object> toMap() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("attack_tool", attack_tool);
        details.put("attack_damage", attack_damage);
        details.put("victim_health_before", victim_health_before);
        details.put("victim_health_after", victim_health_after);
        return details;
    }
}
