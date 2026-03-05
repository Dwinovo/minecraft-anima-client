package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TamedEventReporter {

    private static final String TAMED_VERB = "minecraft.tamed";
    private static final long REPORT_COOLDOWN_TICKS = 200L;
    private static final long COOLDOWN_RETENTION_TICKS = REPORT_COOLDOWN_TICKS * 8L;
    private static final long CLEANUP_INTERVAL_TICKS = 100L;

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-tamed-event-reporter");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<TamedKey, Long> LAST_REPORT_TICKS = new ConcurrentHashMap<>();

    private TamedEventReporter() {
    }

    public static void onEntityTamed(Entity tameTarget, Entity tamer, String hookSource) {
        if (tameTarget.level() == null || tameTarget.level().isClientSide()) {
            return;
        }

        UUID tamerUuid = tamer.getUUID();
        UUID targetUuid = tameTarget.getUUID();
        if (tamerUuid.equals(targetUuid)) {
            return;
        }

        long worldTime = Math.max(0L, tameTarget.level().getGameTime());
        if (worldTime % CLEANUP_INTERVAL_TICKS == 0L) {
            cleanupCooldown(worldTime);
        }

        TamedKey key = new TamedKey(tamerUuid, targetUuid);
        Long lastReportTick = LAST_REPORT_TICKS.get(key);
        if (lastReportTick != null && worldTime - lastReportTick < REPORT_COOLDOWN_TICKS) {
            return;
        }
        LAST_REPORT_TICKS.put(key, worldTime);

        EntityLifecycle.EntityCredential tamerCredential = EntityLifecycle.findEntityCredential(tamerUuid);
        if (tamerCredential == null) {
            return;
        }

        EntityLifecycle.EntityCredential targetCredential = EntityLifecycle.findEntityCredential(targetUuid);
        if (targetCredential != null && !tamerCredential.sessionId().equals(targetCredential.sessionId())) {
            targetCredential = null;
        }

        String targetRef = resolveTargetRef(tamerCredential, targetCredential, targetUuid);
        JsonObject details = buildDetails(tameTarget, tamer, hookSource);
        REPORT_EXECUTOR.execute(() -> reportTamedEvent(tamerCredential, targetRef, worldTime, details));
    }

    private static String resolveTargetRef(
            EntityLifecycle.EntityCredential subjectCredential,
            EntityLifecycle.EntityCredential targetCredential,
            UUID targetUuid
    ) {
        if (targetCredential != null && subjectCredential.sessionId().equals(targetCredential.sessionId())) {
            return "entity:" + targetCredential.entityId();
        }
        return "entity:" + targetUuid;
    }

    private static JsonObject buildDetails(Entity tameTarget, Entity tamer, String hookSource) {
        JsonObject details = new JsonObject();
        details.addProperty("event_type", "TAMED");
        details.addProperty("hook_source", hookSource);
        details.addProperty("tamer_entity_uuid", tamer.getUUID().toString());
        details.addProperty("tamer_name", resolveDisplayName(tamer));
        details.addProperty("target_entity_uuid", tameTarget.getUUID().toString());
        details.addProperty("target_name", resolveDisplayName(tameTarget));
        details.addProperty("target_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(tameTarget.getType()).toString());

        if (tameTarget instanceof OwnableEntity ownableEntity) {
            UUID ownerUuid = ownableEntity.getOwnerUUID();
            if (ownerUuid != null) {
                details.addProperty("owner_entity_uuid", ownerUuid.toString());
            }
        }

        return details;
    }

    private static void reportTamedEvent(
            EntityLifecycle.EntityCredential tamerCredential,
            String targetRef,
            long worldTime,
            JsonObject details
    ) {
        try {
            API_CLIENT.reportEvent(
                    tamerCredential.sessionId(),
                    tamerCredential.entityId(),
                    tamerCredential.accessToken(),
                    TAMED_VERB,
                    targetRef,
                    details,
                    worldTime
            );
            Constants.LOG.info(
                    "[Anima-Tamed] reported: subject={} target_ref={} verb={} target={}",
                    tamerCredential.displayName(),
                    targetRef,
                    TAMED_VERB,
                    details.get("target_entity_type").getAsString()
            );
        } catch (Exception exception) {
            Constants.LOG.warn(
                    "[Anima-Tamed] failed to report: subject={} target_ref={} verb={}",
                    tamerCredential.displayName(),
                    targetRef,
                    TAMED_VERB,
                    exception
            );
        }
    }

    private static String resolveDisplayName(Entity entity) {
        String name = entity.getName().getString().trim();
        if (!name.isEmpty()) {
            return name;
        }
        return entity.getType().getDescription().getString();
    }

    private static void cleanupCooldown(long worldTime) {
        long minTick = worldTime - COOLDOWN_RETENTION_TICKS;
        for (Map.Entry<TamedKey, Long> entry : LAST_REPORT_TICKS.entrySet()) {
            if (entry.getValue() >= minTick) {
                continue;
            }
            LAST_REPORT_TICKS.remove(entry.getKey(), entry.getValue());
        }
    }

    private record TamedKey(UUID tamerUuid, UUID targetUuid) {
    }
}
