package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProtectedEventReporter {

    private static final String PROTECTED_VERB = "minecraft.protected";
    private static final long PROTECTION_WINDOW_TICKS = 60L;
    private static final long AGGRESSION_RETENTION_TICKS = 240L;
    private static final long REPORT_COOLDOWN_TICKS = 200L;
    private static final long COOLDOWN_RETENTION_TICKS = REPORT_COOLDOWN_TICKS * 6L;
    private static final long CLEANUP_INTERVAL_TICKS = 100L;
    private static final double MAX_PROTECTION_DISTANCE_SQ = 12.0D * 12.0D;

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-protected-event-reporter");
        thread.setDaemon(true);
        return thread;
    });

    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, AggressionState>> AGGRESSION_BY_ATTACKER =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ProtectionKey, Long> LAST_REPORT_TICKS = new ConcurrentHashMap<>();

    private ProtectedEventReporter() {
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
            cleanup(worldTime);
        }

        recordAggression(attacker, victim, Math.max(0.0D, amount), worldTime);
        detectProtection(attacker, victim, worldTime, damageSource);
    }

    private static void recordAggression(LivingEntity attacker, LivingEntity victim, double damageAmount, long worldTime) {
        ConcurrentHashMap<UUID, AggressionState> victimMap = AGGRESSION_BY_ATTACKER.computeIfAbsent(
                attacker.getUUID(),
                ignored -> new ConcurrentHashMap<>()
        );
        victimMap.compute(
                victim.getUUID(),
                (ignored, current) -> {
                    if (current == null) {
                        return new AggressionState(
                                victim.getUUID(),
                                resolveDisplayName(victim),
                                1,
                                damageAmount,
                                worldTime
                        );
                    }
                    return current.update(resolveDisplayName(victim), damageAmount, worldTime);
                }
        );
    }

    private static void detectProtection(
            LivingEntity protector,
            LivingEntity aggressor,
            long worldTime,
            DamageSource triggerDamageSource
    ) {
        UUID protectorUuid = protector.getUUID();
        UUID aggressorUuid = aggressor.getUUID();

        EntityLifecycle.EntityCredential protectorCredential = EntityLifecycle.findEntityCredential(protectorUuid);
        if (protectorCredential == null) {
            return;
        }

        ConcurrentHashMap<UUID, AggressionState> aggressionTargets = AGGRESSION_BY_ATTACKER.get(aggressorUuid);
        if (aggressionTargets == null || aggressionTargets.isEmpty()) {
            return;
        }

        Candidate candidate = null;
        long minTick = worldTime - PROTECTION_WINDOW_TICKS;
        if (!(protector.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (Map.Entry<UUID, AggressionState> entry : aggressionTargets.entrySet()) {
            AggressionState state = entry.getValue();
            UUID protectedUuid = entry.getKey();
            if (protectedUuid.equals(protectorUuid) || protectedUuid.equals(aggressorUuid)) {
                continue;
            }
            if (state.lastHitTick() < minTick) {
                continue;
            }

            Entity protectedEntityRaw = serverLevel.getEntity(protectedUuid);
            if (!(protectedEntityRaw instanceof LivingEntity protectedEntity) || !protectedEntity.isAlive()) {
                continue;
            }
            if (protector.distanceToSqr(protectedEntity) > MAX_PROTECTION_DISTANCE_SQ) {
                continue;
            }

            if (candidate == null || state.lastHitTick() > candidate.state().lastHitTick()) {
                candidate = new Candidate(protectedEntity, state);
            }
        }

        if (candidate == null) {
            return;
        }

        ProtectionKey protectionKey = new ProtectionKey(protectorUuid, candidate.entity().getUUID(), aggressorUuid);
        Long lastReportTick = LAST_REPORT_TICKS.get(protectionKey);
        if (lastReportTick != null && worldTime - lastReportTick < REPORT_COOLDOWN_TICKS) {
            return;
        }

        EntityLifecycle.EntityCredential protectedCredential = EntityLifecycle.findEntityCredential(candidate.entity().getUUID());
        String targetRef = resolveTargetRef(protectorCredential, protectedCredential, candidate.entity().getUUID());
        JsonObject details = buildDetails(
                protector,
                candidate.entity(),
                aggressor,
                candidate.state(),
                triggerDamageSource,
                worldTime
        );

        LAST_REPORT_TICKS.put(protectionKey, worldTime);
        REPORT_EXECUTOR.execute(() -> reportProtectedEvent(protectorCredential, targetRef, worldTime, details));
    }

    private static String resolveTargetRef(
            EntityLifecycle.EntityCredential subject,
            EntityLifecycle.EntityCredential target,
            UUID targetUuid
    ) {
        if (target != null && subject.sessionId().equals(target.sessionId())) {
            return "entity:" + target.entityId();
        }
        return "entity:" + targetUuid;
    }

    private static JsonObject buildDetails(
            LivingEntity protector,
            LivingEntity protectedEntity,
            LivingEntity aggressor,
            AggressionState state,
            DamageSource triggerDamageSource,
            long worldTime
    ) {
        JsonObject details = new JsonObject();
        details.addProperty("event_type", "PROTECTED");
        details.addProperty("protector_entity_uuid", protector.getUUID().toString());
        details.addProperty("protector_name", resolveDisplayName(protector));
        details.addProperty("protected_entity_uuid", protectedEntity.getUUID().toString());
        details.addProperty("protected_name", resolveDisplayName(protectedEntity));
        details.addProperty("aggressor_entity_uuid", aggressor.getUUID().toString());
        details.addProperty("aggressor_name", resolveDisplayName(aggressor));
        details.addProperty("aggressor_recent_hit_count", state.hitCount());
        details.addProperty("aggressor_recent_damage", state.totalDamage());
        details.addProperty("aggressor_last_hit_tick", state.lastHitTick());
        details.addProperty("protection_window_ticks", PROTECTION_WINDOW_TICKS);
        details.addProperty("trigger_world_time", worldTime);
        details.addProperty("trigger_damage_source", triggerDamageSource.getMsgId());
        details.addProperty("protector_to_protected_distance", Math.sqrt(protector.distanceToSqr(protectedEntity)));
        return details;
    }

    private static void reportProtectedEvent(
            EntityLifecycle.EntityCredential protectorCredential,
            String targetRef,
            long worldTime,
            JsonObject details
    ) {
        try {
            API_CLIENT.reportEvent(
                    protectorCredential.sessionId(),
                    protectorCredential.entityId(),
                    protectorCredential.accessToken(),
                    PROTECTED_VERB,
                    targetRef,
                    details,
                    worldTime
            );
            Constants.LOG.info(
                    "[Anima-Protected] reported: subject={} target_ref={} verb={}",
                    protectorCredential.displayName(),
                    targetRef,
                    PROTECTED_VERB
            );
        } catch (Exception exception) {
            Constants.LOG.warn(
                    "[Anima-Protected] failed to report: subject={} target_ref={} verb={}",
                    protectorCredential.displayName(),
                    targetRef,
                    PROTECTED_VERB,
                    exception
            );
        }
    }

    private static void cleanup(long worldTime) {
        long minAggressionTick = worldTime - AGGRESSION_RETENTION_TICKS;
        for (Map.Entry<UUID, ConcurrentHashMap<UUID, AggressionState>> attackerEntry : AGGRESSION_BY_ATTACKER.entrySet()) {
            ConcurrentHashMap<UUID, AggressionState> victimMap = attackerEntry.getValue();
            for (Map.Entry<UUID, AggressionState> victimEntry : victimMap.entrySet()) {
                if (victimEntry.getValue().lastHitTick() >= minAggressionTick) {
                    continue;
                }
                victimMap.remove(victimEntry.getKey(), victimEntry.getValue());
            }
            if (victimMap.isEmpty()) {
                AGGRESSION_BY_ATTACKER.remove(attackerEntry.getKey(), victimMap);
            }
        }

        long minCooldownTick = worldTime - COOLDOWN_RETENTION_TICKS;
        for (Map.Entry<ProtectionKey, Long> cooldownEntry : LAST_REPORT_TICKS.entrySet()) {
            if (cooldownEntry.getValue() >= minCooldownTick) {
                continue;
            }
            LAST_REPORT_TICKS.remove(cooldownEntry.getKey(), cooldownEntry.getValue());
        }
    }

    private static String resolveDisplayName(Entity entity) {
        String name = entity.getName().getString().trim();
        if (!name.isEmpty()) {
            return name;
        }
        return entity.getType().getDescription().getString();
    }

    private record Candidate(LivingEntity entity, AggressionState state) {
    }

    private record ProtectionKey(UUID protectorUuid, UUID protectedUuid, UUID aggressorUuid) {
    }

    private record AggressionState(
            UUID victimUuid,
            String victimName,
            int hitCount,
            double totalDamage,
            long lastHitTick
    ) {
        private AggressionState update(String nextVictimName, double damageDelta, long nextTick) {
            return new AggressionState(
                    victimUuid,
                    nextVictimName,
                    hitCount + 1,
                    totalDamage + damageDelta,
                    Math.max(lastHitTick, nextTick)
            );
        }
    }
}
