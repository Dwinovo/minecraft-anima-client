package com.dwinovo.anima.entity;

import com.dwinovo.anima.Constants;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

public final class AnimaEntityProfileLogger {

    private AnimaEntityProfileLogger() {
    }

    public static void logProfileIfSupported(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }

        Constants.LOG.info(
            "Anima entity loaded: type={}, uuid={}, profile={}",
            BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
            entity.getUUID(),
            AnimaEntityProfileResolver.resolveProfile(entity)
        );
    }
}
