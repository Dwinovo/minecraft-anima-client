package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.session.SessionStore;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class EntityLifecycle {

    private static final int MAX_ENTITY_NAME_LENGTH = 64;
    private static final String ENTITY_SOURCE = "minecraft";

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService LIFECYCLE_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-entity-lifecycle");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicReference<String> CURRENT_SESSION_ID = new AtomicReference<>("");
    private static final AtomicBoolean WORLD_RESCAN_REQUESTED = new AtomicBoolean(false);
    private static final ConcurrentHashMap<UUID, RegisteredEntityState> REGISTERED_ENTITIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap.KeySetView<UUID, Boolean> REGISTERING_ENTITIES = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap.KeySetView<UUID, Boolean> UNREGISTER_WHEN_READY = ConcurrentHashMap.newKeySet();

    private EntityLifecycle() {
    }

    public static void onSessionChanged(String rawSessionId) {
        String nextSessionId = normalizeSessionId(rawSessionId);
        String previousSessionId = CURRENT_SESSION_ID.getAndSet(nextSessionId);
        if (Objects.equals(previousSessionId, nextSessionId)) {
            return;
        }

        Constants.LOG.info("Anima session switched: {} -> {}", printableSession(previousSessionId), printableSession(nextSessionId));
        WORLD_RESCAN_REQUESTED.set(!nextSessionId.isEmpty());
        UNREGISTER_WHEN_READY.addAll(REGISTERING_ENTITIES);

        if (REGISTERED_ENTITIES.isEmpty()) {
            return;
        }
        List<RegisteredEntityState> staleEntities = new ArrayList<>(REGISTERED_ENTITIES.values());
        REGISTERED_ENTITIES.clear();
        for (RegisteredEntityState staleEntity : staleEntities) {
            scheduleUnregister(staleEntity);
        }
    }

    public static void onEntityLoaded(Entity entity) {
        if (!isTrackableEntity(entity)) {
            return;
        }

        syncSessionFromStore();
        String sessionId = CURRENT_SESSION_ID.get();
        if (sessionId.isEmpty()) {
            return;
        }

        UUID entityId = entity.getUUID();
        RegisteredEntityState existing = REGISTERED_ENTITIES.get(entityId);
        if (existing != null && existing.sessionId().equals(sessionId)) {
            return;
        }
        if (existing != null) {
            REGISTERED_ENTITIES.remove(entityId);
            scheduleUnregister(existing);
        }

        if (!REGISTERING_ENTITIES.add(entityId)) {
            return;
        }

        String entityName = resolveEntityName(entity);
        LIFECYCLE_EXECUTOR.execute(() -> registerEntity(entityId, sessionId, entityName, ENTITY_SOURCE));
    }

    public static void onEntityUnloaded(Entity entity) {
        if (!isTrackableEntity(entity)) {
            return;
        }

        syncSessionFromStore();
        UUID entityId = entity.getUUID();
        if (REGISTERING_ENTITIES.contains(entityId)) {
            UNREGISTER_WHEN_READY.add(entityId);
            return;
        }

        RegisteredEntityState state = REGISTERED_ENTITIES.remove(entityId);
        if (state != null) {
            scheduleUnregister(state);
        }
    }

    public static EntityCredential findEntityCredential(UUID entityId) {
        RegisteredEntityState state = REGISTERED_ENTITIES.get(entityId);
        if (state == null) {
            return null;
        }
        return new EntityCredential(
                state.sessionId(),
                state.entityId(),
                state.accessToken(),
                state.displayName()
        );
    }

    public static boolean consumeWorldRescanRequest() {
        return WORLD_RESCAN_REQUESTED.compareAndSet(true, false);
    }

    private static void registerEntity(UUID entityId, String sessionId, String name, String source) {
        try {
            EntityApiClient.RegisteredEntity registeredEntity = API_CLIENT.registerEntity(sessionId, name, source);
            RegisteredEntityState state = new RegisteredEntityState(
                    sessionId,
                    registeredEntity.entityId(),
                    registeredEntity.accessToken(),
                    registeredEntity.displayName()
            );

            boolean shouldUnregister = UNREGISTER_WHEN_READY.remove(entityId) || !sessionId.equals(CURRENT_SESSION_ID.get());
            if (shouldUnregister) {
                scheduleUnregister(state);
                return;
            }

            RegisteredEntityState replaced = REGISTERED_ENTITIES.put(entityId, state);
            if (replaced != null && !replaced.entityId().equals(state.entityId())) {
                scheduleUnregister(replaced);
            }
            Constants.LOG.info("Registered Anima entity '{}' for entity {}", state.displayName(), entityId);
        } catch (Exception exception) {
            UNREGISTER_WHEN_READY.remove(entityId);
            Constants.LOG.warn("Failed to register Anima entity for entity {} in session {}", entityId, sessionId, exception);
        } finally {
            REGISTERING_ENTITIES.remove(entityId);
        }
    }

    private static void scheduleUnregister(RegisteredEntityState state) {
        LIFECYCLE_EXECUTOR.execute(() -> {
            try {
                API_CLIENT.unregisterEntity(state.sessionId(), state.entityId(), state.accessToken());
                Constants.LOG.info("Unregistered Anima entity '{}' ({})", state.displayName(), state.entityId());
            } catch (Exception exception) {
                Constants.LOG.warn(
                        "Failed to unregister Anima entity '{}' ({}) from session {}",
                        state.displayName(),
                        state.entityId(),
                        state.sessionId(),
                        exception
                );
            }
        });
    }

    private static void syncSessionFromStore() {
        onSessionChanged(SessionStore.getSessionId());
    }

    private static boolean isTrackableEntity(Entity entity) {
        return entity instanceof Mob || entity instanceof Player;
    }

    private static String resolveEntityName(Entity entity) {
        String name = entity.getName().getString().trim();
        if (name.isEmpty()) {
            name = entity.getType().getDescription().getString().trim();
        }
        if (name.isEmpty()) {
            String raw = entity.getUUID().toString();
            name = "mob-" + raw.substring(0, Math.min(raw.length(), 8));
        }
        if (name.length() > MAX_ENTITY_NAME_LENGTH) {
            return name.substring(0, MAX_ENTITY_NAME_LENGTH);
        }
        return name;
    }

    private static String normalizeSessionId(String rawSessionId) {
        if (rawSessionId == null) {
            return "";
        }
        return rawSessionId.trim();
    }

    private static String printableSession(String sessionId) {
        return sessionId.isEmpty() ? "(none)" : sessionId;
    }

    public record EntityCredential(String sessionId, String entityId, String accessToken, String displayName) {
    }

    private record RegisteredEntityState(String sessionId, String entityId, String accessToken, String displayName) {
    }
}
