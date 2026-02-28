package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HelpedEventDetector {

    private static final double DISTANCE_RADIUS = 10.0D;

    private final long windowTicks;
    private final Deque<AttackRecord> attackTimeline = new ArrayDeque<>();
    private final Deque<DeathRecord> deathTimeline = new ArrayDeque<>();
    private final Map<String, Deque<AttackRecord>> attacksByAttacker = new HashMap<>();
    private final Map<String, Deque<AttackRecord>> attacksByVictim = new HashMap<>();
    private final Map<String, Deque<DeathRecord>> deathsByKiller = new HashMap<>();
    private final Map<String, Deque<DeathRecord>> deathsByVictim = new HashMap<>();

    HelpedEventDetector(long windowTicks) {
        this.windowTicks = Math.max(1L, windowTicks);
    }

    synchronized List<HelpedCandidate> onAttack(
        EntitySnapshot attacker,
        EntitySnapshot victim,
        float damage,
        long tick
    ) {
        evictExpired(tick);
        if (!isValidPair(attacker, victim)) {
            return List.of();
        }

        AttackRecord attack = new AttackRecord(
            attacker.entityId(),
            victim.entityId(),
            Math.max(0.0F, damage),
            tick,
            attacker.location(),
            attacker,
            victim
        );
        indexAttack(attack);
        return detectCandidates(attacker, victim, tick, false);
    }

    synchronized List<HelpedCandidate> onDeath(
        EntitySnapshot killer,
        EntitySnapshot victim,
        String reason,
        long tick
    ) {
        evictExpired(tick);
        if (victim != null && !isBlank(victim.entityId())) {
            DeathRecord death = new DeathRecord(
                killer == null ? null : killer.entityId(),
                victim.entityId(),
                tick,
                reason,
                victim.location(),
                killer,
                victim
            );
            indexDeath(death);
        }

        if (!isValidPair(killer, victim)) {
            return List.of();
        }
        return detectCandidates(killer, victim, tick, true);
    }

    private List<HelpedCandidate> detectCandidates(
        EntitySnapshot helper,
        EntitySnapshot threat,
        long tick,
        boolean killTrigger
    ) {
        List<AttackRecord> threatAttacks = attacksFrom(threat.entityId(), tick);
        if (threatAttacks.isEmpty()) {
            return List.of();
        }

        Map<String, List<AttackRecord>> hitsByBeneficiary = new LinkedHashMap<>();
        for (AttackRecord attack : threatAttacks) {
            if (attack.victimId().equals(helper.entityId()) || attack.victimId().equals(threat.entityId())) {
                continue;
            }
            hitsByBeneficiary.computeIfAbsent(attack.victimId(), ignored -> new ArrayList<>()).add(attack);
        }
        if (hitsByBeneficiary.isEmpty()) {
            return List.of();
        }

        List<AttackRecord> helperHits = attacksBetween(helper.entityId(), threat.entityId(), tick);
        int helperHitsOnThreat = helperHits.size();
        double helperDamageOnThreat = helperHits.stream()
            .mapToDouble(AttackRecord::damage)
            .sum();
        boolean helperKilledThreat = killTrigger || killedBetween(helper.entityId(), threat.entityId(), tick);
        boolean threatHitHelper = hasAttackBetween(threat.entityId(), helper.entityId(), tick);

        float threatHealthBaseline = threatHealthBaseline(threat.entityId(), threat.maxHealth(), tick);
        List<HelpedCandidate> candidates = new ArrayList<>();

        for (List<AttackRecord> hitsOnBeneficiary : hitsByBeneficiary.values()) {
            hitsOnBeneficiary.sort(Comparator.comparingLong(AttackRecord::tick));
            AttackRecord firstHit = hitsOnBeneficiary.getFirst();
            AttackRecord lastHit = hitsOnBeneficiary.getLast();
            EntitySnapshot beneficiary = lastHit.victimSnapshot();
            if (beneficiary == null || isBlank(beneficiary.entityId()) || beneficiary.entityId().equals(helper.entityId())) {
                continue;
            }

            long latencyTicks = Math.max(0L, tick - lastHit.tick());
            double sTime = clamp01(1.0D - (double) latencyTicks / (double) windowTicks);
            double sDist = scoreDistance(helper.location(), beneficiary.location());
            double sFocus = (helperHitsOnThreat > 0 || helperKilledThreat) ? 1.0D : 0.0D;
            double sPressure = clamp01((double) hitsOnBeneficiary.size() / 3.0D);
            double sImpact = helperKilledThreat
                ? 1.0D
                : clamp01(helperDamageOnThreat / Math.max(threatHealthBaseline, 1.0F));
            double pSelf = threatHitHelper ? 0.25D : 0.0D;
            double pPreEngage = hadPreEngagementBefore(helper.entityId(), threat.entityId(), firstHit.tick()) ? 0.20D : 0.0D;

            double confidence = clamp01(
                0.35D * sTime
                    + 0.20D * sDist
                    + 0.20D * sFocus
                    + 0.15D * sPressure
                    + 0.10D * sImpact
                    - pSelf
                    - pPreEngage
            );

            candidates.add(new HelpedCandidate(
                killTrigger ? "kill_rescue" : "counter_attack",
                helper.entityId(),
                beneficiary.entityId(),
                threat.entityId(),
                latencyTicks,
                confidence,
                hitsOnBeneficiary.size(),
                helperHitsOnThreat,
                helperKilledThreat,
                helper,
                beneficiary
            ));
        }

        return candidates;
    }

    private boolean hadPreEngagementBefore(String helperId, String threatId, long beforeTick) {
        return hasAttackBetweenBefore(helperId, threatId, beforeTick)
            || hasAttackBetweenBefore(threatId, helperId, beforeTick);
    }

    private boolean hasAttackBetweenBefore(String attackerId, String victimId, long beforeTick) {
        if (beforeTick <= Long.MIN_VALUE + 1) {
            return false;
        }
        Deque<AttackRecord> records = attacksByAttacker.get(attackerId);
        if (records == null) {
            return false;
        }
        for (AttackRecord record : records) {
            if (record.victimId().equals(victimId) && record.tick() < beforeTick) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAttackBetween(String attackerId, String victimId, long atOrBeforeTick) {
        Deque<AttackRecord> records = attacksByAttacker.get(attackerId);
        if (records == null) {
            return false;
        }
        for (AttackRecord record : records) {
            if (record.victimId().equals(victimId) && record.tick() <= atOrBeforeTick) {
                return true;
            }
        }
        return false;
    }

    private boolean killedBetween(String killerId, String victimId, long atOrBeforeTick) {
        Deque<DeathRecord> records = deathsByKiller.get(killerId);
        if (records == null) {
            return false;
        }
        for (DeathRecord record : records) {
            if (record.victimId().equals(victimId) && record.tick() <= atOrBeforeTick) {
                return true;
            }
        }
        return false;
    }

    private List<AttackRecord> attacksFrom(String attackerId, long atOrBeforeTick) {
        Deque<AttackRecord> records = attacksByAttacker.get(attackerId);
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        List<AttackRecord> result = new ArrayList<>();
        for (AttackRecord record : records) {
            if (record.tick() <= atOrBeforeTick) {
                result.add(record);
            }
        }
        return result;
    }

    private List<AttackRecord> attacksBetween(String attackerId, String victimId, long atOrBeforeTick) {
        Deque<AttackRecord> records = attacksByAttacker.get(attackerId);
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        List<AttackRecord> result = new ArrayList<>();
        for (AttackRecord record : records) {
            if (record.victimId().equals(victimId) && record.tick() <= atOrBeforeTick) {
                result.add(record);
            }
        }
        return result;
    }

    private float threatHealthBaseline(String threatId, float fallback, long atOrBeforeTick) {
        float baseline = Math.max(1.0F, fallback);

        Deque<AttackRecord> attackRecords = attacksByVictim.get(threatId);
        if (attackRecords != null) {
            for (AttackRecord record : attackRecords) {
                if (record.tick() <= atOrBeforeTick && record.victimSnapshot() != null) {
                    baseline = Math.max(baseline, record.victimSnapshot().maxHealth());
                }
            }
        }

        Deque<DeathRecord> deathRecords = deathsByVictim.get(threatId);
        if (deathRecords != null) {
            for (DeathRecord record : deathRecords) {
                if (record.tick() <= atOrBeforeTick && record.victimSnapshot() != null) {
                    baseline = Math.max(baseline, record.victimSnapshot().maxHealth());
                }
            }
        }

        return Math.max(1.0F, baseline);
    }

    private void evictExpired(long tick) {
        long minTick = tick - windowTicks;

        while (!attackTimeline.isEmpty()) {
            AttackRecord oldest = attackTimeline.peekFirst();
            if (oldest == null || oldest.tick() >= minTick) {
                break;
            }
            removeAttack(attackTimeline.removeFirst());
        }

        while (!deathTimeline.isEmpty()) {
            DeathRecord oldest = deathTimeline.peekFirst();
            if (oldest == null || oldest.tick() >= minTick) {
                break;
            }
            removeDeath(deathTimeline.removeFirst());
        }
    }

    private void indexAttack(AttackRecord attack) {
        attackTimeline.addLast(attack);
        indexByKey(attacksByAttacker, attack.attackerId(), attack);
        indexByKey(attacksByVictim, attack.victimId(), attack);
    }

    private void indexDeath(DeathRecord death) {
        deathTimeline.addLast(death);
        indexByKey(deathsByKiller, death.killerId(), death);
        indexByKey(deathsByVictim, death.victimId(), death);
    }

    private void removeAttack(AttackRecord attack) {
        removeByKey(attacksByAttacker, attack.attackerId(), attack);
        removeByKey(attacksByVictim, attack.victimId(), attack);
    }

    private void removeDeath(DeathRecord death) {
        removeByKey(deathsByKiller, death.killerId(), death);
        removeByKey(deathsByVictim, death.victimId(), death);
    }

    private static <T> void indexByKey(Map<String, Deque<T>> map, String key, T value) {
        if (isBlank(key)) {
            return;
        }
        map.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(value);
    }

    private static <T> void removeByKey(Map<String, Deque<T>> map, String key, T value) {
        if (isBlank(key)) {
            return;
        }
        Deque<T> values = map.get(key);
        if (values == null) {
            return;
        }
        values.removeFirstOccurrence(value);
        if (values.isEmpty()) {
            map.remove(key);
        }
    }

    private static boolean isValidPair(EntitySnapshot attacker, EntitySnapshot victim) {
        return attacker != null
            && victim != null
            && !isBlank(attacker.entityId())
            && !isBlank(victim.entityId())
            && !attacker.entityId().equals(victim.entityId());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static double scoreDistance(LocationSnapshot helper, LocationSnapshot beneficiary) {
        if (helper == null || beneficiary == null) {
            return 0.0D;
        }
        if (helper.dimension() != null
            && beneficiary.dimension() != null
            && !helper.dimension().equals(beneficiary.dimension())) {
            return 0.0D;
        }

        double dx = helper.x() - beneficiary.x();
        double dy = helper.y() - beneficiary.y();
        double dz = helper.z() - beneficiary.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return clamp01(1.0D - (distance / DISTANCE_RADIUS));
    }

    private static double clamp01(double value) {
        if (value <= 0.0D) {
            return 0.0D;
        }
        if (value >= 1.0D) {
            return 1.0D;
        }
        return value;
    }

    record LocationSnapshot(
        String dimension,
        String biome,
        double x,
        double y,
        double z
    ) {
        EventRequest.LocationRequest toRequest() {
            return new EventRequest.LocationRequest(
                dimension,
                biome,
                new double[] {x, y, z}
            );
        }
    }

    record EntitySnapshot(
        String entityId,
        String entityType,
        String name,
        LocationSnapshot location,
        float health,
        float maxHealth
    ) {
        EventRequest.EntityRequest toRequest() {
            return new EventRequest.EntityRequest(
                entityId,
                entityType,
                name,
                location == null ? null : location.toRequest(),
                new EventRequest.EntityStateRequest(health, maxHealth)
            );
        }
    }

    record HelpedCandidate(
        String kind,
        String helperId,
        String beneficiaryId,
        String threatId,
        long latencyTicks,
        double confidence,
        int recentHitsOnBeneficiary,
        int helperHitsOnThreat,
        boolean helperKilledThreat,
        EntitySnapshot helper,
        EntitySnapshot beneficiary
    ) {}

    private record AttackRecord(
        String attackerId,
        String victimId,
        float damage,
        long tick,
        LocationSnapshot position,
        EntitySnapshot attackerSnapshot,
        EntitySnapshot victimSnapshot
    ) {}

    private record DeathRecord(
        String killerId,
        String victimId,
        long tick,
        String reason,
        LocationSnapshot position,
        EntitySnapshot killerSnapshot,
        EntitySnapshot victimSnapshot
    ) {}
}
