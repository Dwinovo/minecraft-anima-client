package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventRequest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EntityAttackEventFactory {

    private static final EventRequest.EntityStateRequest EMPTY_STATE =
        new EventRequest.EntityStateRequest(0.0F, 0.0F);

    private EntityAttackEventFactory() {}

    public static EventRequest build(
        String sessionId,
        LivingEntity target,
        DamageSource damageSource,
        float damageAmount
    ) {
        Entity attacker = damageSource.getEntity();
        String damageType = damageSource.type().msgId();
        String sourceEntityType = toEntityType(attacker);
        String weapon = toWeapon(attacker);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("damage", damageAmount);
        details.put("damage_type", damageType);
        details.put("damage_source_entity_type", sourceEntityType);
        details.put("weapon", weapon);

        return new EventRequest(
            sessionId,
            target.level().getGameTime(),
            Instant.now().toString(),
            toSubject(attacker, target),
            new EventRequest.ActionRequest("ATTACKED", details),
            toEntity(target)
        );
    }

    private static EventRequest.EntityRequest toSubject(Entity attacker, LivingEntity target) {
        if (attacker == null) {
            return new EventRequest.EntityRequest(
                "environment",
                "minecraft:environment",
                "Environment",
                toLocation(target),
                EMPTY_STATE
            );
        }
        return toEntity(attacker);
    }

    private static EventRequest.EntityRequest toEntity(Entity entity) {
        if (entity == null) {
            return null;
        }
        return new EventRequest.EntityRequest(
            entity.getUUID().toString(),
            toEntityType(entity),
            entity.getName().getString(),
            toLocation(entity),
            toState(entity)
        );
    }

    private static EventRequest.LocationRequest toLocation(Entity entity) {
        if (entity == null) {
            return null;
        }
        String biome = entity.level()
            .getBiome(entity.blockPosition())
            .unwrapKey()
            .map(resourceKey -> resourceKey.location().toString())
            .orElse("minecraft:unknown");
        return new EventRequest.LocationRequest(
            entity.level().dimension().location().toString(),
            biome,
            new double[] {entity.getX(), entity.getY(), entity.getZ()}
        );
    }

    private static EventRequest.EntityStateRequest toState(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return new EventRequest.EntityStateRequest(
                living.getHealth(),
                living.getMaxHealth()
            );
        }
        return EMPTY_STATE;
    }

    private static String toEntityType(Entity entity) {
        if (entity == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private static String toWeapon(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(living.getMainHandItem().getItem()).toString();
    }
}
