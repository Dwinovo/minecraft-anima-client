package com.dwinovo.anima.telemetry.model.details;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public record KilledDetails(
    String death_reason
) {
    public static KilledDetails fromNative(DamageSource damageSource, LivingEntity victim) {
        return new KilledDetails(damageSource.getLocalizedDeathMessage(victim).getString());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("death_reason", death_reason);
        return details;
    }
}
