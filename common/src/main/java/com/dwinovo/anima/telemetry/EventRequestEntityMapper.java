package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventRequest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class EventRequestEntityMapper {

    private static final EventRequest.EntityStateRequest EMPTY_STATE =
        new EventRequest.EntityStateRequest(0.0F, 0.0F);

    private EventRequestEntityMapper() {}

    static EventRequest.EntityRequest toEnvironment(Entity referenceEntity) {
        return new EventRequest.EntityRequest(
            "environment",
            "minecraft:environment",
            "Environment",
            toLocation(referenceEntity),
            EMPTY_STATE
        );
    }

    static EventRequest.EntityRequest toEntity(Entity entity) {
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

    static EventRequest.LocationRequest toLocation(Entity entity) {
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

    static String toEntityType(Entity entity) {
        if (entity == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    static String toWeapon(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(living.getMainHandItem().getItem()).toString();
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
}
