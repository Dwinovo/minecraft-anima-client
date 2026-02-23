package com.dwinovo.anima.telemetry.model.details;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KilledDetailsTest {

    @Test
    void mapsDeathReasonField() {
        KilledDetails details = new KilledDetails("Steve was slain by Zombie");

        Map<String, Object> mapped = details.toMap();

        assertEquals("Steve was slain by Zombie", mapped.get("death_reason"));
    }
}
