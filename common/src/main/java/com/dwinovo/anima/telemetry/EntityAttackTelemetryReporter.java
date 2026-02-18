package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.entity.IAnimaEntity;
import com.dwinovo.anima.telemetry.model.EventRequest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class EntityAttackTelemetryReporter {

    private EntityAttackTelemetryReporter() {}

    public static void reportIfSupported(
        LivingEntity target,
        DamageSource damageSource,
        float damageAmount,
        String source
    ) {
        if (target.level().isClientSide() || !(target instanceof IAnimaEntity)) {
            return;
        }

        MinecraftServer server = target.level().getServer();
        if (server == null) {
            return;
        }

        String sessionId = SessionRegistrationService.getOrCreateSessionId(server);
        EventRequest payload = EntityAttackEventFactory.build(sessionId, target, damageSource, damageAmount);
        AnimaApiClient.postEvent(payload, source).thenAccept(data -> {
            if (data == null) {
                Constants.LOG.warn("[{}] Event upload failed for entity_uuid={}", source, target.getUUID());
                return;
            }

            String ackSessionId = data.session_id();
            if (ackSessionId == null || ackSessionId.isBlank()) {
                Constants.LOG.warn("[{}] Event upload acknowledged without session_id for entity_uuid={}", source, target.getUUID());
                return;
            }

            if (!sessionId.equals(ackSessionId)) {
                Constants.LOG.warn(
                    "[{}] Event upload acknowledged with mismatched session_id={} (expected={}) for entity_uuid={}",
                    source,
                    ackSessionId,
                    sessionId,
                    target.getUUID()
                );
                return;
            }

            Constants.LOG.info("[{}] Event created, session_id={}, entity_uuid={}", source, ackSessionId, target.getUUID());
        });
    }
}
