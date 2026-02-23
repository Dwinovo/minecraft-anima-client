package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventVerb;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SocialEventDetailsFactoryTest {

    @Test
    void returnsUnifiedDeathVerb() {
        assertEquals(EventVerb.KILLED, SocialEventDetailsFactory.deathVerb());
    }

    @Test
    void trimsAndLimitsChatMessage() {
        String longMessage = "   " + "x".repeat(600) + "   ";

        String normalized = SocialEventDetailsFactory.normalizeChatMessage(longMessage);

        assertEquals(512, normalized.length());
        assertFalse(normalized.startsWith(" "));
    }

    @Test
    void buildsChatDetailsWithLength() {
        Map<String, Object> details = SocialEventDetailsFactory.chatDetails("hello");

        assertEquals("hello", details.get("message"));
        assertEquals(5, details.get("message_length"));
        assertEquals("player_chat", details.get("channel"));
    }
}
