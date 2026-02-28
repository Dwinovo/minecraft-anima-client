package com.dwinovo.anima.telemetry.agent;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.entity.IAnimaEntity;
import com.dwinovo.anima.telemetry.api.AnimaApiClient;
import com.dwinovo.anima.telemetry.model.AgentActivateRequest;
import com.dwinovo.anima.telemetry.model.AgentDeactivateRequest;
import com.dwinovo.anima.telemetry.session.SessionRegistrationService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimaAgentLifecycleService {

    private static final Map<String, Integer> AGENT_VERSIONS = new ConcurrentHashMap<>();

    private AnimaAgentLifecycleService() {}

    public static void activateOnEntityLoaded(Entity entity, String source) {
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

        AgentActivateRequest payload = new AgentActivateRequest(sessionId, entityUuid, entityType, profile);
        AnimaApiClient.postAgentActivate(payload, source).thenAccept(data -> {
            if (data == null) {
                Constants.LOG.warn("[{}] Agent activate failed for entity_uuid={}, entity_type={}", source, entityUuid, entityType);
                return;
            }

            cacheVersion(sessionId, entityUuid, data.version());
            Constants.LOG.info(
                "[{}] Agent activated: thread_id={}, lifecycle_status={}, version={}, entity_uuid={}",
                source,
                data.thread_id(),
                data.lifecycle_status(),
                data.version(),
                entityUuid
            );
        });
    }

    public static void deactivateOnEntityUnloaded(Entity entity, String source) {
        if (entity.level().isClientSide() || !(entity instanceof IAnimaEntity)) {
            return;
        }

        MinecraftServer server = entity.level().getServer();
        if (server == null) {
            return;
        }

        String sessionId = SessionRegistrationService.getOrCreateSessionId(server);
        String entityUuid = entity.getUUID().toString();
        Integer cachedVersion = AGENT_VERSIONS.get(threadId(sessionId, entityUuid));
        int expectedVersion = cachedVersion == null ? 1 : cachedVersion;
        if (cachedVersion == null) {
            Constants.LOG.warn(
                "[{}] Missing cached agent version for entity_uuid={}, fallback expected_version=1",
                source,
                entityUuid
            );
        }
        AgentDeactivateRequest payload = new AgentDeactivateRequest(sessionId, entityUuid, expectedVersion, true);

        AnimaApiClient.deleteAgentDeactivate(payload, source).thenAccept(data -> {
            if (data == null) {
                Constants.LOG.warn(
                    "[{}] Agent deactivate failed for entity_uuid={}, expected_version={}",
                    source,
                    entityUuid,
                    expectedVersion
                );
                return;
            }

            AGENT_VERSIONS.remove(threadId(sessionId, entityUuid));
            Constants.LOG.info(
                "[{}] Agent deactivated: thread_id={}, lifecycle_status={}, version={}, entity_uuid={}",
                source,
                data.thread_id(),
                data.lifecycle_status(),
                data.version(),
                entityUuid
            );
        });
    }

    private static void cacheVersion(String sessionId, String entityUuid, Integer version) {
        if (version == null) {
            return;
        }
        AGENT_VERSIONS.put(threadId(sessionId, entityUuid), version);
    }

    private static String threadId(String sessionId, String entityUuid) {
        return sessionId + ":" + entityUuid;
    }
}

