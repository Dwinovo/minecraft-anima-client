package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AssistEventReporter {

    private static final String ASSIST_VERB = "minecraft.assist";
    private static final long ASSIST_WINDOW_TICKS = 200L;
    private static final long STATE_RETENTION_TICKS = 400L;
    private static final long CLEANUP_INTERVAL_TICKS = 100L;
    private static final int MAX_ASSIST_REPORTS_PER_DEATH = 3;

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-assist-event-reporter");
        thread.setDaemon(true);
        return thread;
    });

    private static final ConcurrentHashMap<UUID, VictimDamageState> DAMAGE_STATES = new ConcurrentHashMap<>();

    private AssistEventReporter() {
    }

    public static void onEntityDamaged(LivingEntity victim, DamageSource damageSource, float amount) {
        if (victim.level() == null || victim.level().isClientSide()) {
            return;
        }

        Entity attackerEntity = damageSource.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker)) {
            return;
        }

        UUID attackerUuid = attacker.getUUID();
        UUID victimUuid = victim.getUUID();
        if (attackerUuid.equals(victimUuid)) {
            return;
        }

        long worldTime = Math.max(0L, victim.level().getGameTime());
        if (worldTime % CLEANUP_INTERVAL_TICKS == 0L) {
            cleanupExpiredStates(worldTime);
        }

        VictimDamageState state = DAMAGE_STATES.computeIfAbsent(
                victimUuid,
                ignored -> new VictimDamageState(victimUuid, resolveDisplayName(victim), worldTime)
        );
        state.recordDamage(attacker, Math.max(0.0D, amount), worldTime);
    }

    public static void onEntityDied(LivingEntity victim, DamageSource damageSource) {
        if (victim.level() == null || victim.level().isClientSide()) {
            return;
        }

        long worldTime = Math.max(0L, victim.level().getGameTime());
        VictimDamageState state = DAMAGE_STATES.remove(victim.getUUID());
        if (state == null) {
            return;
        }

        UUID killerUuid = resolveKillerUuid(damageSource, state, worldTime);
        if (killerUuid == null) {
            Constants.LOG.info("[Anima-Assist] skipped: unable to resolve killer for victim={}", victim.getUUID());
            return;
        }

        EntityLifecycle.EntityCredential killerCredential = EntityLifecycle.findEntityCredential(killerUuid);
        if (killerCredential == null) {
            Constants.LOG.info("[Anima-Assist] skipped: killer {} is not a registered Anima entity", killerUuid);
            return;
        }

        List<AttackerSnapshot> contributors = state.collectAssistContributors(killerUuid, worldTime - ASSIST_WINDOW_TICKS);
        if (contributors.isEmpty()) {
            return;
        }

        int reportCount = 0;
        for (AttackerSnapshot contributor : contributors) {
            if (reportCount >= MAX_ASSIST_REPORTS_PER_DEATH) {
                break;
            }

            EntityLifecycle.EntityCredential assistantCredential = EntityLifecycle.findEntityCredential(contributor.attackerUuid());
            if (assistantCredential == null) {
                continue;
            }
            if (!assistantCredential.sessionId().equals(killerCredential.sessionId())) {
                continue;
            }

            String targetRef = "entity:" + killerCredential.entityId();
            JsonObject details = buildDetails(victim, state, killerUuid, killerCredential, contributor, damageSource);
            REPORT_EXECUTOR.execute(() -> reportAssistEvent(assistantCredential, targetRef, worldTime, details));
            reportCount += 1;
        }
    }

    private static UUID resolveKillerUuid(DamageSource damageSource, VictimDamageState state, long worldTime) {
        Entity killerEntity = damageSource.getEntity();
        if (killerEntity instanceof LivingEntity livingKiller) {
            return livingKiller.getUUID();
        }
        return state.findMostRecentAttacker(worldTime - ASSIST_WINDOW_TICKS);
    }

    private static void cleanupExpiredStates(long worldTime) {
        for (Map.Entry<UUID, VictimDamageState> entry : DAMAGE_STATES.entrySet()) {
            VictimDamageState state = entry.getValue();
            if (!state.isExpired(worldTime)) {
                continue;
            }
            DAMAGE_STATES.remove(entry.getKey(), state);
        }
    }

    private static JsonObject buildDetails(
            LivingEntity victim,
            VictimDamageState state,
            UUID killerUuid,
            EntityLifecycle.EntityCredential killerCredential,
            AttackerSnapshot contributor,
            DamageSource finalDamageSource
    ) {
        JsonObject details = new JsonObject();
        details.addProperty("event_type", "ASSIST");
        details.addProperty("assistant_entity_uuid", contributor.attackerUuid().toString());
        details.addProperty("assistant_name", contributor.attackerName());
        details.addProperty("assistant_hit_count", contributor.hitCount());
        details.addProperty("assistant_total_damage", contributor.totalDamage());
        details.addProperty("assistant_last_weapon_id", contributor.lastWeaponId());
        details.addProperty("killer_entity_uuid", killerUuid.toString());
        details.addProperty("killer_entity_id", killerCredential.entityId());
        details.addProperty("killer_display_name", killerCredential.displayName());
        details.addProperty("victim_entity_uuid", victim.getUUID().toString());
        details.addProperty("victim_name", state.victimName());
        details.addProperty("victim_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString());
        details.addProperty("assist_window_ticks", ASSIST_WINDOW_TICKS);
        details.addProperty("participant_count", state.participantCount());
        details.addProperty("final_damage_source", finalDamageSource.getMsgId());
        return details;
    }

    private static void reportAssistEvent(
            EntityLifecycle.EntityCredential assistantCredential,
            String targetRef,
            long worldTime,
            JsonObject details
    ) {
        try {
            API_CLIENT.reportEvent(
                    assistantCredential.sessionId(),
                    assistantCredential.entityId(),
                    assistantCredential.accessToken(),
                    ASSIST_VERB,
                    targetRef,
                    details,
                    worldTime
            );
            Constants.LOG.info(
                    "[Anima-Assist] reported: subject={} target_ref={} verb={}",
                    assistantCredential.displayName(),
                    targetRef,
                    ASSIST_VERB
            );
        } catch (Exception exception) {
            Constants.LOG.warn(
                    "[Anima-Assist] failed to report: subject={} target_ref={} verb={}",
                    assistantCredential.displayName(),
                    targetRef,
                    ASSIST_VERB,
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

    private static String resolveWeaponId(LivingEntity attacker) {
        ItemStack mainHand = attacker.getMainHandItem();
        if (mainHand.isEmpty()) {
            return "minecraft:air";
        }
        return BuiltInRegistries.ITEM.getKey(mainHand.getItem()).toString();
    }

    private static final class VictimDamageState {
        private final String victimName;
        private final Map<UUID, AttackerContribution> contributions = new HashMap<>();
        private long lastUpdateTick;

        private VictimDamageState(UUID victimUuid, String victimName, long worldTime) {
            this.victimName = victimName;
            this.lastUpdateTick = worldTime;
        }

        private synchronized void recordDamage(
                LivingEntity attacker,
                double damageAmount,
                long worldTime
        ) {
            UUID attackerUuid = attacker.getUUID();
            AttackerContribution contribution = contributions.computeIfAbsent(
                    attackerUuid,
                    ignored -> new AttackerContribution(attackerUuid, resolveDisplayName(attacker))
            );
            contribution.hitCount += 1;
            contribution.totalDamage += damageAmount;
            contribution.lastHitTick = worldTime;
            contribution.lastWeaponId = resolveWeaponId(attacker);
            lastUpdateTick = Math.max(lastUpdateTick, worldTime);
        }

        private synchronized List<AttackerSnapshot> collectAssistContributors(UUID killerUuid, long minTick) {
            List<AttackerSnapshot> snapshots = new ArrayList<>();
            for (AttackerContribution contribution : contributions.values()) {
                if (contribution.attackerUuid.equals(killerUuid)) {
                    continue;
                }
                if (contribution.lastHitTick < minTick) {
                    continue;
                }
                snapshots.add(new AttackerSnapshot(
                        contribution.attackerUuid,
                        contribution.attackerName,
                        contribution.hitCount,
                        contribution.totalDamage,
                        contribution.lastWeaponId,
                        contribution.lastHitTick
                ));
            }
            snapshots.sort((left, right) -> {
                int damageOrder = Double.compare(right.totalDamage(), left.totalDamage());
                if (damageOrder != 0) {
                    return damageOrder;
                }
                int hitOrder = Integer.compare(right.hitCount(), left.hitCount());
                if (hitOrder != 0) {
                    return hitOrder;
                }
                return Long.compare(right.lastHitTick(), left.lastHitTick());
            });
            return snapshots;
        }

        private synchronized UUID findMostRecentAttacker(long minTick) {
            UUID result = null;
            long resultTick = Long.MIN_VALUE;
            for (AttackerContribution contribution : contributions.values()) {
                if (contribution.lastHitTick < minTick) {
                    continue;
                }
                if (contribution.lastHitTick <= resultTick) {
                    continue;
                }
                result = contribution.attackerUuid;
                resultTick = contribution.lastHitTick;
            }
            return result;
        }

        private synchronized boolean isExpired(long worldTime) {
            return worldTime - lastUpdateTick > STATE_RETENTION_TICKS;
        }

        private synchronized int participantCount() {
            return contributions.size();
        }

        private String victimName() {
            return victimName;
        }
    }

    private static final class AttackerContribution {
        private final UUID attackerUuid;
        private final String attackerName;
        private int hitCount;
        private double totalDamage;
        private long lastHitTick;
        private String lastWeaponId;

        private AttackerContribution(UUID attackerUuid, String attackerName) {
            this.attackerUuid = attackerUuid;
            this.attackerName = attackerName;
            this.hitCount = 0;
            this.totalDamage = 0.0D;
            this.lastHitTick = Long.MIN_VALUE;
            this.lastWeaponId = "minecraft:air";
        }
    }

    private record AttackerSnapshot(
            UUID attackerUuid,
            String attackerName,
            int hitCount,
            double totalDamage,
            String lastWeaponId,
            long lastHitTick
    ) {
    }
}
