package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CuredEventReporter {

    private static final String CURED_VERB = "minecraft.cured";
    private static final long REPORT_COOLDOWN_TICKS = 200L;
    private static final long COOLDOWN_RETENTION_TICKS = 2000L;
    private static final long CLEANUP_INTERVAL_TICKS = 100L;

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-cured-event-reporter");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<UUID, Long> LAST_REPORT_BY_ZOMBIE = new ConcurrentHashMap<>();

    private CuredEventReporter() {
    }

    public static void onZombieVillagerCured(
            ZombieVillager zombieVillager,
            Villager villager,
            UUID conversionStarterUuid
    ) {
        if (zombieVillager.level() == null || zombieVillager.level().isClientSide()) {
            return;
        }
        if (villager == null) {
            return;
        }

        long worldTime = Math.max(0L, zombieVillager.level().getGameTime());
        if (worldTime % CLEANUP_INTERVAL_TICKS == 0L) {
            cleanupCooldown(worldTime);
        }

        UUID zombieUuid = zombieVillager.getUUID();
        Long lastReportTick = LAST_REPORT_BY_ZOMBIE.get(zombieUuid);
        if (lastReportTick != null && worldTime - lastReportTick < REPORT_COOLDOWN_TICKS) {
            return;
        }
        LAST_REPORT_BY_ZOMBIE.put(zombieUuid, worldTime);

        SubjectSelection subjectSelection = resolveSubject(zombieVillager, conversionStarterUuid);
        if (subjectSelection.subjectCredential() == null) {
            return;
        }

        EntityLifecycle.EntityCredential villagerCredential = EntityLifecycle.findEntityCredential(villager.getUUID());
        if (villagerCredential != null
                && !subjectSelection.subjectCredential().sessionId().equals(villagerCredential.sessionId())) {
            villagerCredential = null;
        }

        String targetRef = resolveTargetRef(subjectSelection.subjectCredential(), villagerCredential, villager.getUUID());
        JsonObject details = buildDetails(zombieVillager, villager, conversionStarterUuid, subjectSelection.subjectRole());
        REPORT_EXECUTOR.execute(() -> reportCuredEvent(subjectSelection.subjectCredential(), targetRef, worldTime, details));
    }

    private static SubjectSelection resolveSubject(ZombieVillager zombieVillager, UUID conversionStarterUuid) {
        if (conversionStarterUuid != null) {
            EntityLifecycle.EntityCredential starterCredential = EntityLifecycle.findEntityCredential(conversionStarterUuid);
            if (starterCredential != null) {
                return new SubjectSelection(starterCredential, "conversion_starter");
            }
        }

        EntityLifecycle.EntityCredential zombieCredential = EntityLifecycle.findEntityCredential(zombieVillager.getUUID());
        if (zombieCredential != null) {
            return new SubjectSelection(zombieCredential, "zombie_villager");
        }

        return new SubjectSelection(null, "none");
    }

    private static JsonObject buildDetails(
            ZombieVillager zombieVillager,
            Villager villager,
            UUID conversionStarterUuid,
            String subjectRole
    ) {
        JsonObject details = new JsonObject();
        details.addProperty("event_type", "CURED");
        details.addProperty("subject_role", subjectRole);

        details.addProperty("zombie_villager_uuid", zombieVillager.getUUID().toString());
        details.addProperty("zombie_villager_name", resolveDisplayName(zombieVillager));
        details.addProperty(
                "zombie_villager_type",
                BuiltInRegistries.ENTITY_TYPE.getKey(zombieVillager.getType()).toString()
        );

        details.addProperty("villager_uuid", villager.getUUID().toString());
        details.addProperty("villager_name", resolveDisplayName(villager));
        details.addProperty("villager_type", BuiltInRegistries.ENTITY_TYPE.getKey(villager.getType()).toString());
        details.addProperty(
                "villager_profession",
                BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).toString()
        );
        details.addProperty("villager_level", villager.getVillagerData().getLevel());
        details.addProperty("villager_xp", villager.getVillagerXp());

        if (conversionStarterUuid != null) {
            details.addProperty("conversion_starter_uuid", conversionStarterUuid.toString());
        }

        return details;
    }

    private static void reportCuredEvent(
            EntityLifecycle.EntityCredential subjectCredential,
            String targetRef,
            long worldTime,
            JsonObject details
    ) {
        try {
            API_CLIENT.reportEvent(
                    subjectCredential.sessionId(),
                    subjectCredential.entityId(),
                    subjectCredential.accessToken(),
                    CURED_VERB,
                    targetRef,
                    details,
                    worldTime
            );
            Constants.LOG.info(
                    "[Anima-Cured] reported: subject={} target_ref={} verb={} profession={}",
                    subjectCredential.displayName(),
                    targetRef,
                    CURED_VERB,
                    details.get("villager_profession").getAsString()
            );
        } catch (Exception exception) {
            Constants.LOG.warn(
                    "[Anima-Cured] failed to report: subject={} target_ref={} verb={}",
                    subjectCredential.displayName(),
                    targetRef,
                    CURED_VERB,
                    exception
            );
        }
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

    private static String resolveDisplayName(Entity entity) {
        String name = entity.getName().getString().trim();
        if (!name.isEmpty()) {
            return name;
        }
        return entity.getType().getDescription().getString();
    }

    private static void cleanupCooldown(long worldTime) {
        long minTick = worldTime - COOLDOWN_RETENTION_TICKS;
        for (Map.Entry<UUID, Long> entry : LAST_REPORT_BY_ZOMBIE.entrySet()) {
            if (entry.getValue() >= minTick) {
                continue;
            }
            LAST_REPORT_BY_ZOMBIE.remove(entry.getKey(), entry.getValue());
        }
    }

    private record SubjectSelection(EntityLifecycle.EntityCredential subjectCredential, String subjectRole) {
    }
}
