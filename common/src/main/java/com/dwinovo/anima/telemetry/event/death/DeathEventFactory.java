package com.dwinovo.anima.telemetry.event.death;

import com.dwinovo.anima.telemetry.event.core.EventRequestAssembler;
import com.dwinovo.anima.telemetry.event.core.EventRequestEntityMapper;
import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventVerb;
import com.dwinovo.anima.telemetry.model.details.KilledDetails;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class DeathEventFactory {

    private DeathEventFactory() {}

    public static EventRequest buildLivingDeathEvent(
        String sessionId,
        LivingEntity victim,
        DamageSource damageSource
    ) {
        Entity killer = damageSource.getEntity();
        KilledDetails details = KilledDetails.fromNative(damageSource, victim);

        return EventRequestAssembler.build(
            sessionId,
            victim.level().getGameTime(),
            killer == null ? EventRequestEntityMapper.toEnvironment(victim) : EventRequestEntityMapper.toEntity(killer),
            EventVerb.KILLED,
            details,
            EventRequestEntityMapper.toEntity(victim)
        );
    }
}

