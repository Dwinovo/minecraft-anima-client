package com.dwinovo.anima.telemetry.event.helped;

import com.dwinovo.anima.telemetry.event.core.EventRequestAssembler;
import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventVerb;
import com.dwinovo.anima.telemetry.model.details.HelpedDetails;

final class HelpedEventFactory {

    private HelpedEventFactory() {}

    static EventRequest build(
        String sessionId,
        long worldTime,
        HelpedEventDetector.HelpedCandidate candidate
    ) {
        HelpedDetails details = new HelpedDetails(
            candidate.kind(),
            candidate.helperId(),
            candidate.beneficiaryId(),
            candidate.threatId(),
            candidate.latencyTicks(),
            candidate.confidence(),
            candidate.recentHitsOnBeneficiary(),
            candidate.helperHitsOnThreat(),
            candidate.helperKilledThreat()
        );

        return EventRequestAssembler.build(
            sessionId,
            worldTime,
            candidate.helper().toRequest(),
            EventVerb.HELPED,
            details,
            candidate.beneficiary().toRequest()
        );
    }
}

