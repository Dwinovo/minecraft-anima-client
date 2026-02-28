package com.dwinovo.anima.telemetry.model.details;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HelpedDetailsTest {

    @Test
    void mapsHelpedFieldsAndEvidence() {
        HelpedDetails details = new HelpedDetails(
            "counter_attack",
            "helper-1",
            "beneficiary-1",
            "threat-1",
            2L,
            0.86D,
            1,
            2,
            false
        );

        Map<String, Object> mapped = details.toMap();

        assertEquals("counter_attack", mapped.get("kind"));
        assertEquals("helper-1", mapped.get("helper_id"));
        assertEquals("beneficiary-1", mapped.get("beneficiary_id"));
        assertEquals("threat-1", mapped.get("threat_id"));
        assertEquals(2L, mapped.get("latency_ticks"));
        assertEquals(0.86D, mapped.get("confidence"));

        Object evidence = mapped.get("evidence");
        assertInstanceOf(Map.class, evidence);
        @SuppressWarnings("unchecked")
        Map<String, Object> evidenceMap = (Map<String, Object>) evidence;
        assertEquals(1, evidenceMap.get("recent_hits_on_b"));
        assertEquals(2, evidenceMap.get("helper_hits_on_threat"));
        assertEquals(false, evidenceMap.get("helper_killed_threat"));
    }
}
