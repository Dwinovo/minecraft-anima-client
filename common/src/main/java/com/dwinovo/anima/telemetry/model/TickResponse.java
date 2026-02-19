package com.dwinovo.anima.telemetry.model;

import java.util.Collections;
import java.util.List;

public record TickResponse(
    int code,
    String message,
    TickDataResponse data
) {
    public record TickDataResponse(
        String session_id,
        int total_agents,
        int succeeded,
        int failed,
        List<TickAgentResultResponse> results
    ) {
        public TickDataResponse {
            results = results == null ? Collections.emptyList() : results;
        }
    }

    public record TickAgentResultResponse(
        String agent_uuid,
        boolean success,
        String message
    ) {}
}
