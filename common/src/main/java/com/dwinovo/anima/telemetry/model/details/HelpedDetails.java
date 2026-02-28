package com.dwinovo.anima.telemetry.model.details;

import java.util.LinkedHashMap;
import java.util.Map;

public record HelpedDetails(
    String kind,
    String helper_id,
    String beneficiary_id,
    String threat_id,
    long latency_ticks,
    double confidence,
    int recent_hits_on_b,
    int helper_hits_on_threat,
    boolean helper_killed_threat
) implements EventDetails {
    public Map<String, Object> toMap() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("recent_hits_on_b", recent_hits_on_b);
        evidence.put("helper_hits_on_threat", helper_hits_on_threat);
        evidence.put("helper_killed_threat", helper_killed_threat);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", kind);
        details.put("helper_id", helper_id);
        details.put("beneficiary_id", beneficiary_id);
        details.put("threat_id", threat_id);
        details.put("latency_ticks", latency_ticks);
        details.put("confidence", confidence);
        details.put("evidence", evidence);
        return details;
    }
}
