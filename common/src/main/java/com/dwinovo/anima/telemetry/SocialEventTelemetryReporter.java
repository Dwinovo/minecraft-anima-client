package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.telemetry.model.EventRequest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

public final class SocialEventTelemetryReporter {

    private static final String[] CHAT_MESSAGE_ACCESSORS = new String[] {
        "signedContent",
        "content",
        "plain",
        "getSignedContent",
        "getContent",
        "getString"
    };

    private SocialEventTelemetryReporter() {}

    public static void reportLivingDeath(
        LivingEntity victim,
        DamageSource damageSource,
        String source
    ) {
        if (victim.level().isClientSide()) {
            return;
        }

        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return;
        }

        String sessionId = SessionRegistrationService.getOrCreateSessionId(server);
        EventRequest payload = SocialEventFactory.buildLivingDeathEvent(sessionId, victim, damageSource);
        uploadEvent(sessionId, payload, source, victim.getUUID().toString());
    }

    public static void reportPlayerChat(
        Object playerSource,
        String rawMessage,
        String source
    ) {
        if (!(playerSource instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        String normalizedMessage = SocialEventDetailsFactory.normalizeChatMessage(rawMessage);
        if (normalizedMessage.isBlank()) {
            return;
        }

        MinecraftServer server = player.serverLevel().getServer();
        if (server == null) {
            return;
        }

        String sessionId = SessionRegistrationService.getOrCreateSessionId(server);
        EventRequest payload = SocialEventFactory.buildPlayerChatEvent(sessionId, player, normalizedMessage);
        uploadEvent(sessionId, payload, source, player.getUUID().toString());
    }

    public static String extractChatMessage(Object rawMessagePayload) {
        if (rawMessagePayload == null) {
            return "";
        }
        if (rawMessagePayload instanceof String rawMessage) {
            return rawMessage;
        }

        for (String methodName : CHAT_MESSAGE_ACCESSORS) {
            try {
                Method accessor = rawMessagePayload.getClass().getMethod(methodName);
                Object value = accessor.invoke(rawMessagePayload);
                if (value instanceof String stringValue) {
                    return stringValue;
                }
                if (value != null) {
                    return value.toString();
                }
            } catch (ReflectiveOperationException ignored) {
                // Ignore and try next accessor.
            }
        }

        return rawMessagePayload.toString();
    }

    private static void uploadEvent(
        String sessionId,
        EventRequest payload,
        String source,
        String subjectUuid
    ) {
        AnimaApiClient.postEvent(payload, source).thenAccept(data -> {
            if (data == null) {
                Constants.LOG.warn("[{}] Social event upload failed for subject_uuid={}", source, subjectUuid);
                return;
            }

            String ackSessionId = data.session_id();
            if (ackSessionId == null || ackSessionId.isBlank()) {
                Constants.LOG.warn("[{}] Social event upload acknowledged without session_id for subject_uuid={}", source, subjectUuid);
                return;
            }

            if (!sessionId.equals(ackSessionId)) {
                Constants.LOG.warn(
                    "[{}] Social event upload acknowledged with mismatched session_id={} (expected={}) for subject_uuid={}",
                    source,
                    ackSessionId,
                    sessionId,
                    subjectUuid
                );
                return;
            }

            Constants.LOG.info("[{}] Social event created, session_id={}, subject_uuid={}", source, ackSessionId, subjectUuid);
        });
    }
}
