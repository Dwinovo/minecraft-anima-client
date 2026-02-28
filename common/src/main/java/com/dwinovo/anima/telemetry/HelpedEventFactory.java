package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventVerb;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class HelpedEventFactory {

    private HelpedEventFactory() {}

    static EventRequest build(
        String sessionId,
        long worldTime,
        HelpedEventDetector.HelpedCandidate candidate
    ) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("recent_hits_on_b", candidate.recentHitsOnBeneficiary());
        evidence.put("helper_hits_on_threat", candidate.helperHitsOnThreat());
        evidence.put("helper_killed_threat", candidate.helperKilledThreat());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", candidate.kind());
        details.put("helper_id", candidate.helperId());
        details.put("beneficiary_id", candidate.beneficiaryId());
        details.put("threat_id", candidate.threatId());
        details.put("latency_ticks", candidate.latencyTicks());
        details.put("confidence", candidate.confidence());
        details.put("evidence", evidence);

        return new EventRequest(
            sessionId,
            worldTime,
            Instant.now().toString(),
            candidate.helper().toRequest(),
            new EventRequest.ActionRequest(EventVerb.HELPED.value(), details),
            candidate.beneficiary().toRequest()
        );
    }
}
