package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventRequest;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class HelpedEventTelemetryReporter {

    private static final HelpedEventPipeline PIPELINE = new HelpedEventPipeline(
        new HelpedEventDetector(HelpedEventPipeline.DEFAULT_WINDOW_TICKS),
        AnimaApiClient::postEventWithStatus,
        HelpedEventPipeline.Config.fromEnvironment()
    );

    private HelpedEventTelemetryReporter() {}

    public static void reportAttack(
        LivingEntity target,
        DamageSource damageSource,
        float damageAmount,
        String source,
        String sessionId
    ) {
        if (target == null || damageSource == null || target.level().isClientSide()) {
            return;
        }

        Entity attacker = damageSource.getEntity();
        if (attacker == null) {
            return;
        }

        PIPELINE.onAttack(
            toSnapshot(attacker),
            toSnapshot(target),
            damageAmount,
            target.level().getGameTime(),
            sessionId,
            source
        );
    }

    public static void reportDeath(
        LivingEntity victim,
        DamageSource damageSource,
        String source,
        String sessionId
    ) {
        if (victim == null || victim.level().isClientSide()) {
            return;
        }

        Entity killer = damageSource == null ? null : damageSource.getEntity();
        PIPELINE.onDeath(
            toSnapshot(killer),
            toSnapshot(victim),
            damageSource == null ? "unknown" : damageSource.getLocalizedDeathMessage(victim).getString(),
            victim.level().getGameTime(),
            sessionId,
            source
        );
    }

    private static HelpedEventDetector.EntitySnapshot toSnapshot(Entity entity) {
        if (entity == null) {
            return null;
        }

        EventRequest.LocationRequest location = EventRequestEntityMapper.toLocation(entity);
        double[] coordinates = location == null ? null : location.coordinates();
        HelpedEventDetector.LocationSnapshot locationSnapshot = null;
        if (location != null && coordinates != null && coordinates.length >= 3) {
            locationSnapshot = new HelpedEventDetector.LocationSnapshot(
                location.dimension(),
                location.biome(),
                coordinates[0],
                coordinates[1],
                coordinates[2]
            );
        }

        float health = entity instanceof LivingEntity living ? living.getHealth() : 0.0F;
        float maxHealth = entity instanceof LivingEntity living ? living.getMaxHealth() : 0.0F;
        return new HelpedEventDetector.EntitySnapshot(
            entity.getUUID().toString(),
            EventRequestEntityMapper.toEntityType(entity),
            entity.getName().getString(),
            locationSnapshot,
            health,
            maxHealth
        );
    }
}
