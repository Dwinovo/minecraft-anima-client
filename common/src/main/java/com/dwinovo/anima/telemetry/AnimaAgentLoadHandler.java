package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.entity.IAnimaEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

public final class AnimaAgentLoadHandler {

    private AnimaAgentLoadHandler() {
    }

    public static void onEntityLoaded(Entity entity, String source) {
        if (entity.level().isClientSide() || !(entity instanceof IAnimaEntity animaEntity)) {
            return;
        }

        MinecraftServer server = entity.level().getServer();
        if (server == null) {
            return;
        }

        String sessionId = SessionRegistrationService.getOrCreateSessionId(server);
        String entityUuid = entity.getUUID().toString();
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        String profile = animaEntity.profile();

        AnimaApiClient.postAgentInit(sessionId, entityUuid, entityType, profile, source)
            .thenAccept(success -> {
                if (!success) {
                    Constants.LOG.warn("[{}] Agent init failed for entity_uuid={}, entity_type={}", source, entityUuid, entityType);
                }
            });
    }
}
