package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.entity.IAnimaEntity;
import com.google.gson.JsonObject;
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
        JsonObject payload = EntityAttackEventFactory.build(sessionId, target, damageSource, damageAmount);
        AnimaApiClient.postEvent(payload, source).thenAccept(data -> {
            if (data == null) {
                Constants.LOG.warn("[{}] Event upload failed for entity_uuid={}", source, target.getUUID());
            }
        });
    }
}
