package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BredEventReporter {

    private static final String BRED_VERB = "minecraft.bred";
    private static final long REPORT_COOLDOWN_TICKS = 40L;
    private static final long COOLDOWN_RETENTION_TICKS = 800L;
    private static final long CLEANUP_INTERVAL_TICKS = 100L;

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-bred-event-reporter");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<UUID, Long> LAST_REPORT_BY_CHILD = new ConcurrentHashMap<>();

    private BredEventReporter() {
    }

    public static void onAnimalBred(Animal parentA, Animal parentB, AgeableMob child, String hookSource) {
        if (parentA.level() == null || parentA.level().isClientSide()) {
            return;
        }
        if (child == null) {
            return;
        }

        long worldTime = Math.max(0L, parentA.level().getGameTime());
        if (worldTime % CLEANUP_INTERVAL_TICKS == 0L) {
            cleanupCooldown(worldTime);
        }

        UUID childUuid = child.getUUID();
        Long lastReportTick = LAST_REPORT_BY_CHILD.get(childUuid);
        if (lastReportTick != null && worldTime - lastReportTick < REPORT_COOLDOWN_TICKS) {
            return;
        }
        LAST_REPORT_BY_CHILD.put(childUuid, worldTime);

        SubjectSelection subjectSelection = resolveSubject(parentA, parentB);
        if (subjectSelection.subjectCredential() == null) {
            return;
        }

        EntityLifecycle.EntityCredential childCredential = EntityLifecycle.findEntityCredential(childUuid);
        if (childCredential != null && !subjectSelection.subjectCredential().sessionId().equals(childCredential.sessionId())) {
            childCredential = null;
        }

        String targetRef = resolveTargetRef(subjectSelection.subjectCredential(), childCredential, childUuid);
        JsonObject details = buildDetails(parentA, parentB, child, subjectSelection.subjectRole(), subjectSelection.playerCause(), hookSource);
        REPORT_EXECUTOR.execute(() -> reportBredEvent(subjectSelection.subjectCredential(), targetRef, worldTime, details));
    }

    private static SubjectSelection resolveSubject(Animal parentA, Animal parentB) {
        ServerPlayer playerCauseA = parentA.getLoveCause();
        if (playerCauseA != null) {
            EntityLifecycle.EntityCredential playerCredential = EntityLifecycle.findEntityCredential(playerCauseA.getUUID());
            if (playerCredential != null) {
                return new SubjectSelection(playerCredential, "player", playerCauseA);
            }
        }

        ServerPlayer playerCauseB = parentB.getLoveCause();
        if (playerCauseB != null) {
            EntityLifecycle.EntityCredential playerCredential = EntityLifecycle.findEntityCredential(playerCauseB.getUUID());
            if (playerCredential != null) {
                return new SubjectSelection(playerCredential, "player", playerCauseB);
            }
        }

        EntityLifecycle.EntityCredential parentACredential = EntityLifecycle.findEntityCredential(parentA.getUUID());
        if (parentACredential != null) {
            return new SubjectSelection(parentACredential, "parent_a", playerCauseA != null ? playerCauseA : playerCauseB);
        }

        EntityLifecycle.EntityCredential parentBCredential = EntityLifecycle.findEntityCredential(parentB.getUUID());
        if (parentBCredential != null) {
            return new SubjectSelection(parentBCredential, "parent_b", playerCauseA != null ? playerCauseA : playerCauseB);
        }

        return new SubjectSelection(null, "none", playerCauseA != null ? playerCauseA : playerCauseB);
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

    private static JsonObject buildDetails(
            Animal parentA,
            Animal parentB,
            AgeableMob child,
            String subjectRole,
            ServerPlayer playerCause,
            String hookSource
    ) {
        JsonObject details = new JsonObject();
        details.addProperty("event_type", "BRED");
        details.addProperty("hook_source", hookSource);
        details.addProperty("subject_role", subjectRole);

        details.addProperty("parent_a_entity_uuid", parentA.getUUID().toString());
        details.addProperty("parent_a_name", resolveDisplayName(parentA));
        details.addProperty("parent_a_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(parentA.getType()).toString());

        details.addProperty("parent_b_entity_uuid", parentB.getUUID().toString());
        details.addProperty("parent_b_name", resolveDisplayName(parentB));
        details.addProperty("parent_b_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(parentB.getType()).toString());

        details.addProperty("child_entity_uuid", child.getUUID().toString());
        details.addProperty("child_name", resolveDisplayName(child));
        details.addProperty("child_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(child.getType()).toString());
        details.addProperty("child_is_baby", child.isBaby());

        if (playerCause != null) {
            details.addProperty("love_cause_player_uuid", playerCause.getUUID().toString());
            details.addProperty("love_cause_player_name", resolveDisplayName(playerCause));
        }

        return details;
    }

    private static void reportBredEvent(
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
                    BRED_VERB,
                    targetRef,
                    details,
                    worldTime
            );
            Constants.LOG.info(
                    "[Anima-Bred] reported: subject={} target_ref={} verb={} child={}",
                    subjectCredential.displayName(),
                    targetRef,
                    BRED_VERB,
                    details.get("child_entity_type").getAsString()
            );
        } catch (Exception exception) {
            Constants.LOG.warn(
                    "[Anima-Bred] failed to report: subject={} target_ref={} verb={}",
                    subjectCredential.displayName(),
                    targetRef,
                    BRED_VERB,
                    exception
            );
        }
    }

    private static void cleanupCooldown(long worldTime) {
        long minTick = worldTime - COOLDOWN_RETENTION_TICKS;
        for (Map.Entry<UUID, Long> entry : LAST_REPORT_BY_CHILD.entrySet()) {
            if (entry.getValue() >= minTick) {
                continue;
            }
            LAST_REPORT_BY_CHILD.remove(entry.getKey(), entry.getValue());
        }
    }

    private static String resolveDisplayName(Entity entity) {
        String name = entity.getName().getString().trim();
        if (!name.isEmpty()) {
            return name;
        }
        return entity.getType().getDescription().getString();
    }

    private record SubjectSelection(
            EntityLifecycle.EntityCredential subjectCredential,
            String subjectRole,
            ServerPlayer playerCause
    ) {
    }
}
