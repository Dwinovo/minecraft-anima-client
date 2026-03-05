package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ConflictEventReporter {

    private static final String CONFLICT_VERB = "minecraft.conflict";
    private static final long MIN_DURATION_TICKS = 40L;
    private static final long INACTIVITY_TIMEOUT_MS = 8_000L;
    private static final long REPORT_COOLDOWN_MS = 60_000L;
    private static final long COOLDOWN_RETENTION_MS = REPORT_COOLDOWN_MS * 4;

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-conflict-event-reporter");
        thread.setDaemon(true);
        return thread;
    });
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "anima-conflict-timeout");
        thread.setDaemon(true);
        return thread;
    });

    private static final ConcurrentHashMap<ConflictKey, ConflictState> ACTIVE_CONFLICTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ConflictKey, Long> LAST_REPORT_TIMES = new ConcurrentHashMap<>();

    static {
        TIMEOUT_EXECUTOR.scheduleAtFixedRate(ConflictEventReporter::flushExpiredConflicts, 1L, 1L, TimeUnit.SECONDS);
    }

    private ConflictEventReporter() {
    }

    public static void onEntityDamaged(LivingEntity victim, DamageSource damageSource, float damageAmount) {
        if (victim.level() == null || victim.level().isClientSide()) {
            return;
        }

        Entity sourceEntity = damageSource.getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }

        UUID attackerId = attacker.getUUID();
        UUID victimId = victim.getUUID();
        if (attackerId.equals(victimId)) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        long worldTime = Math.max(0L, victim.level().getGameTime());
        ConflictKey conflictKey = ConflictKey.of(attackerId, victimId);

        ConflictState state = ACTIVE_CONFLICTS.computeIfAbsent(conflictKey, key -> {
            String firstName = key.first().equals(attackerId) ? resolveDisplayName(attacker) : resolveDisplayName(victim);
            String secondName = key.second().equals(victimId) ? resolveDisplayName(victim) : resolveDisplayName(attacker);
            return new ConflictState(key.first(), key.second(), firstName, secondName, worldTime, nowMs);
        });

        String firstName = conflictKey.first().equals(attackerId) ? resolveDisplayName(attacker) : resolveDisplayName(victim);
        String secondName = conflictKey.second().equals(victimId) ? resolveDisplayName(victim) : resolveDisplayName(attacker);
        String weaponId = resolveWeaponId(attacker);
        state.recordHit(attackerId, firstName, secondName, Math.max(0.0D, damageAmount), weaponId, worldTime, nowMs);
    }

    public static void onEntityDied(LivingEntity victim, DamageSource damageSource) {
        if (victim.level() == null || victim.level().isClientSide()) {
            return;
        }
        UUID victimId = victim.getUUID();
        for (Map.Entry<ConflictKey, ConflictState> entry : ACTIVE_CONFLICTS.entrySet()) {
            if (!entry.getKey().contains(victimId)) {
                continue;
            }
            tryFinalize(entry.getKey(), entry.getValue(), "killed");
        }
    }

    private static void flushExpiredConflicts() {
        long nowMs = System.currentTimeMillis();
        for (Map.Entry<ConflictKey, ConflictState> entry : ACTIVE_CONFLICTS.entrySet()) {
            if (!entry.getValue().isExpired(nowMs)) {
                continue;
            }
            tryFinalize(entry.getKey(), entry.getValue(), "timeout");
        }
        pruneCooldown(nowMs);
    }

    private static void pruneCooldown(long nowMs) {
        for (Map.Entry<ConflictKey, Long> entry : LAST_REPORT_TIMES.entrySet()) {
            if (nowMs - entry.getValue() <= COOLDOWN_RETENTION_MS) {
                continue;
            }
            LAST_REPORT_TIMES.remove(entry.getKey(), entry.getValue());
        }
    }

    private static void tryFinalize(ConflictKey key, ConflictState state, String endReason) {
        if (!ACTIVE_CONFLICTS.remove(key, state)) {
            return;
        }

        ConflictSnapshot snapshot = state.snapshot(key, endReason);
        if (!passesThreshold(snapshot)) {
            Constants.LOG.info(
                    "[Anima-Conflict] dropped: pair={} hits={} damage={} duration_ticks={} reason={}",
                    snapshot.pairKey(),
                    snapshot.hitCount(),
                    snapshot.totalDamage(),
                    snapshot.durationTicks(),
                    snapshot.endReason()
            );
            return;
        }

        long nowMs = System.currentTimeMillis();
        Long lastReportedAt = LAST_REPORT_TIMES.get(key);
        if (lastReportedAt != null && nowMs - lastReportedAt < REPORT_COOLDOWN_MS) {
            Constants.LOG.info("[Anima-Conflict] dropped by cooldown: pair={}", snapshot.pairKey());
            return;
        }

        EntityLifecycle.EntityCredential firstCredential = EntityLifecycle.findEntityCredential(snapshot.firstEntityUuid());
        EntityLifecycle.EntityCredential secondCredential = EntityLifecycle.findEntityCredential(snapshot.secondEntityUuid());
        ReporterSelection reporter = selectReporter(snapshot, firstCredential, secondCredential);
        if (reporter == null) {
            Constants.LOG.info("[Anima-Conflict] dropped: no registered subject for pair={}", snapshot.pairKey());
            return;
        }

        String targetRef = buildTargetRef(reporter.subjectCredential(), reporter.targetCredential(), reporter.targetUuid());
        JsonObject details = buildDetails(snapshot, reporter);
        LAST_REPORT_TIMES.put(key, nowMs);

        REPORT_EXECUTOR.execute(() -> reportConflictEvent(reporter.subjectCredential(), targetRef, snapshot, details));
    }

    private static boolean passesThreshold(ConflictSnapshot snapshot) {
        return snapshot.durationTicks() >= MIN_DURATION_TICKS;
    }

    private static ReporterSelection selectReporter(
            ConflictSnapshot snapshot,
            EntityLifecycle.EntityCredential firstCredential,
            EntityLifecycle.EntityCredential secondCredential
    ) {
        if (firstCredential == null && secondCredential == null) {
            return null;
        }
        if (firstCredential != null && secondCredential != null) {
            if (snapshot.firstHits() >= snapshot.secondHits()) {
                return new ReporterSelection(firstCredential, snapshot.firstEntityUuid(), snapshot.secondEntityUuid(), secondCredential);
            }
            return new ReporterSelection(secondCredential, snapshot.secondEntityUuid(), snapshot.firstEntityUuid(), firstCredential);
        }
        if (firstCredential != null) {
            return new ReporterSelection(firstCredential, snapshot.firstEntityUuid(), snapshot.secondEntityUuid(), secondCredential);
        }
        return new ReporterSelection(secondCredential, snapshot.secondEntityUuid(), snapshot.firstEntityUuid(), firstCredential);
    }

    private static String buildTargetRef(
            EntityLifecycle.EntityCredential subjectCredential,
            EntityLifecycle.EntityCredential targetCredential,
            UUID targetUuid
    ) {
        if (targetCredential != null && subjectCredential.sessionId().equals(targetCredential.sessionId())) {
            return "entity:" + targetCredential.entityId();
        }
        return "entity:" + targetUuid;
    }

    private static JsonObject buildDetails(ConflictSnapshot snapshot, ReporterSelection reporter) {
        JsonObject details = new JsonObject();
        details.addProperty("event_type", "CONFLICT");
        details.addProperty("pair_key", snapshot.pairKey());
        details.addProperty("participant_a_uuid", snapshot.firstEntityUuid().toString());
        details.addProperty("participant_a_name", snapshot.firstName());
        details.addProperty("participant_b_uuid", snapshot.secondEntityUuid().toString());
        details.addProperty("participant_b_name", snapshot.secondName());
        details.addProperty("hits_by_a", snapshot.firstHits());
        details.addProperty("hits_by_b", snapshot.secondHits());
        details.addProperty("hit_count", snapshot.hitCount());
        details.addProperty("total_damage", snapshot.totalDamage());
        details.addProperty("duration_ticks", snapshot.durationTicks());
        details.addProperty("both_sides_attacked", snapshot.bothSidesAttacked());
        details.addProperty("top_weapon_id", snapshot.topWeaponId());
        details.addProperty("end_reason", snapshot.endReason());
        details.addProperty("subject_entity_uuid", reporter.subjectUuid().toString());
        details.addProperty("target_entity_uuid", reporter.targetUuid().toString());
        if (reporter.targetCredential() != null) {
            details.addProperty("target_entity_id", reporter.targetCredential().entityId());
        }
        return details;
    }

    private static void reportConflictEvent(
            EntityLifecycle.EntityCredential subjectCredential,
            String targetRef,
            ConflictSnapshot snapshot,
            JsonObject details
    ) {
        try {
            API_CLIENT.reportEvent(
                    subjectCredential.sessionId(),
                    subjectCredential.entityId(),
                    subjectCredential.accessToken(),
                    CONFLICT_VERB,
                    targetRef,
                    details,
                    snapshot.lastWorldTime()
            );
            Constants.LOG.info(
                    "[Anima-Conflict] reported: verb={} subject={} target_ref={} hits={} damage={}",
                    CONFLICT_VERB,
                    subjectCredential.displayName(),
                    targetRef,
                    snapshot.hitCount(),
                    snapshot.totalDamage()
            );
        } catch (Exception exception) {
            Constants.LOG.warn(
                    "[Anima-Conflict] failed to report: verb={} subject={} pair={}",
                    CONFLICT_VERB,
                    subjectCredential.displayName(),
                    snapshot.pairKey(),
                    exception
            );
        }
    }

    private static String resolveDisplayName(Entity entity) {
        String customName = entity.getName().getString().trim();
        if (!customName.isEmpty()) {
            return customName;
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

    private record ConflictKey(UUID first, UUID second) {
        private static ConflictKey of(UUID a, UUID b) {
            if (a.compareTo(b) <= 0) {
                return new ConflictKey(a, b);
            }
            return new ConflictKey(b, a);
        }

        private boolean contains(UUID entityId) {
            return first.equals(entityId) || second.equals(entityId);
        }

        private String pairKey() {
            return first + "|" + second;
        }
    }

    private static final class ConflictState {
        private final UUID firstEntityUuid;
        private final UUID secondEntityUuid;
        private final Map<String, Integer> weaponHits = new HashMap<>();

        private String firstName;
        private String secondName;
        private long firstWorldTime;
        private long lastWorldTime;
        private long lastUpdateAtMs;
        private int firstHits;
        private int secondHits;
        private int hitCount;
        private double totalDamage;
        private boolean bothSidesAttacked;

        private ConflictState(
                UUID firstEntityUuid,
                UUID secondEntityUuid,
                String firstName,
                String secondName,
                long worldTime,
                long updateAtMs
        ) {
            this.firstEntityUuid = firstEntityUuid;
            this.secondEntityUuid = secondEntityUuid;
            this.firstName = firstName;
            this.secondName = secondName;
            this.firstWorldTime = worldTime;
            this.lastWorldTime = worldTime;
            this.lastUpdateAtMs = updateAtMs;
        }

        private synchronized void recordHit(
                UUID attackerId,
                String firstName,
                String secondName,
                double damage,
                String weaponId,
                long worldTime,
                long nowMs
        ) {
            this.firstName = firstName;
            this.secondName = secondName;
            if (worldTime < firstWorldTime) {
                firstWorldTime = worldTime;
            }
            if (worldTime > lastWorldTime) {
                lastWorldTime = worldTime;
            }
            lastUpdateAtMs = nowMs;
            hitCount += 1;
            totalDamage += damage;

            if (firstEntityUuid.equals(attackerId)) {
                firstHits += 1;
            } else if (secondEntityUuid.equals(attackerId)) {
                secondHits += 1;
            }
            bothSidesAttacked = firstHits > 0 && secondHits > 0;

            int nextCount = weaponHits.getOrDefault(weaponId, 0) + 1;
            weaponHits.put(weaponId, nextCount);
        }

        private synchronized boolean isExpired(long nowMs) {
            return nowMs - lastUpdateAtMs >= INACTIVITY_TIMEOUT_MS;
        }

        private synchronized ConflictSnapshot snapshot(ConflictKey key, String endReason) {
            return new ConflictSnapshot(
                    key.pairKey(),
                    firstEntityUuid,
                    secondEntityUuid,
                    firstName,
                    secondName,
                    firstHits,
                    secondHits,
                    hitCount,
                    totalDamage,
                    Math.max(0L, lastWorldTime - firstWorldTime),
                    bothSidesAttacked,
                    resolveTopWeaponId(),
                    endReason,
                    lastWorldTime
            );
        }

        private String resolveTopWeaponId() {
            String bestWeapon = "minecraft:air";
            int bestCount = -1;
            for (Map.Entry<String, Integer> entry : weaponHits.entrySet()) {
                if (entry.getValue() <= bestCount) {
                    continue;
                }
                bestWeapon = entry.getKey();
                bestCount = entry.getValue();
            }
            return bestWeapon;
        }
    }

    private record ConflictSnapshot(
            String pairKey,
            UUID firstEntityUuid,
            UUID secondEntityUuid,
            String firstName,
            String secondName,
            int firstHits,
            int secondHits,
            int hitCount,
            double totalDamage,
            long durationTicks,
            boolean bothSidesAttacked,
            String topWeaponId,
            String endReason,
            long lastWorldTime
    ) {
    }

    private record ReporterSelection(
            EntityLifecycle.EntityCredential subjectCredential,
            UUID subjectUuid,
            UUID targetUuid,
            EntityLifecycle.EntityCredential targetCredential
    ) {
    }
}
