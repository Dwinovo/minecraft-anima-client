package com.dwinovo.anima.telemetry.event.helped;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpedEventDetectorTest {

    @Test
    void recognizesRescueCandidateWithHighConfidence() {
        HelpedEventDetector detector = new HelpedEventDetector(60);
        HelpedEventDetector.EntitySnapshot helper = entity("helper", 0.0, 0.0, 20.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot beneficiary = entity("beneficiary", 2.0, 0.0, 12.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot threat = entity("threat", 1.0, 0.0, 20.0F, 20.0F);

        detector.onAttack(threat, beneficiary, 6.0F, 100L);
        List<HelpedEventDetector.HelpedCandidate> candidates = detector.onAttack(helper, threat, 12.0F, 102L);

        assertEquals(1, candidates.size());
        HelpedEventDetector.HelpedCandidate candidate = candidates.getFirst();
        assertEquals("helper", candidate.helperId());
        assertEquals("beneficiary", candidate.beneficiaryId());
        assertEquals("threat", candidate.threatId());
        assertEquals("counter_attack", candidate.kind());
        assertTrue(candidate.confidence() >= 0.80D);
        assertEquals(2L, candidate.latencyTicks());
        assertEquals(1, candidate.recentHitsOnBeneficiary());
        assertEquals(1, candidate.helperHitsOnThreat());
        assertFalse(candidate.helperKilledThreat());
    }

    @Test
    void lowersConfidenceForSelfDefensePatterns() {
        HelpedEventDetector detector = new HelpedEventDetector(60);
        HelpedEventDetector.EntitySnapshot helper = entity("helper", 0.0, 0.0, 20.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot beneficiary = entity("beneficiary", 2.0, 0.0, 12.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot threat = entity("threat", 1.0, 0.0, 20.0F, 20.0F);

        detector.onAttack(threat, beneficiary, 6.0F, 100L);
        detector.onAttack(threat, helper, 4.0F, 101L);

        List<HelpedEventDetector.HelpedCandidate> candidates = detector.onAttack(helper, threat, 12.0F, 102L);

        assertEquals(1, candidates.size());
        assertTrue(candidates.getFirst().confidence() < 0.80D);
    }

    @Test
    void lowersConfidenceWhenHelperPreEngagedThreatBeforeBeneficiaryWasAttacked() {
        HelpedEventDetector detector = new HelpedEventDetector(60);
        HelpedEventDetector.EntitySnapshot helper = entity("helper", 0.0, 0.0, 20.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot beneficiary = entity("beneficiary", 2.0, 0.0, 12.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot threat = entity("threat", 1.0, 0.0, 20.0F, 20.0F);

        detector.onAttack(helper, threat, 2.0F, 98L);
        detector.onAttack(threat, beneficiary, 6.0F, 100L);

        List<HelpedEventDetector.HelpedCandidate> candidates = detector.onAttack(helper, threat, 12.0F, 102L);

        assertEquals(1, candidates.size());
        assertTrue(candidates.getFirst().confidence() < 0.80D);
    }

    private static HelpedEventDetector.EntitySnapshot entity(
        String id,
        double x,
        double z,
        float health,
        float maxHealth
    ) {
        return new HelpedEventDetector.EntitySnapshot(
            id,
            "minecraft:zombie",
            id,
            new HelpedEventDetector.LocationSnapshot("minecraft:overworld", "minecraft:plains", x, 64.0D, z),
            health,
            maxHealth
        );
    }
}

