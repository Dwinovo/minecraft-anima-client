package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventVerb;

import java.util.LinkedHashMap;
import java.util.Map;

final class SocialEventDetailsFactory {

    private static final int MAX_CHAT_MESSAGE_LENGTH = 512;

    private SocialEventDetailsFactory() {}

    static EventVerb deathVerb() {
        return EventVerb.KILLED;
    }

    static String normalizeChatMessage(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }

        String trimmed = rawMessage.trim();
        if (trimmed.length() <= MAX_CHAT_MESSAGE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_CHAT_MESSAGE_LENGTH);
    }

    static Map<String, Object> chatDetails(String message) {
        String normalized = normalizeChatMessage(message);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("channel", "player_chat");
        details.put("message", normalized);
        details.put("message_length", normalized.length());
        return details;
    }
}
