package com.dwinovo.anima.telemetry.model.details;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttackedDetailsTest {

    @Test
    void mapsRequiredAttackFields() {
        AttackedDetails details = new AttackedDetails("minecraft:iron_sword", 7.0F, 20.0F, 13.0F);

        Map<String, Object> mapped = details.toMap();

        assertEquals("minecraft:iron_sword", mapped.get("attack_tool"));
        assertEquals(7.0F, mapped.get("attack_damage"));
        assertEquals(20.0F, mapped.get("victim_health_before"));
        assertEquals(13.0F, mapped.get("victim_health_after"));
    }
}
