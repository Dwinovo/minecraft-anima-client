package com.dwinovo.anima.telemetry.event.attack;

import com.dwinovo.anima.telemetry.event.core.EventRequestAssembler;
import com.dwinovo.anima.telemetry.event.core.EventRequestEntityMapper;
import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventVerb;
import com.dwinovo.anima.telemetry.model.details.AttackedDetails;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class EntityAttackEventFactory {

    private EntityAttackEventFactory() {}

    public static EventRequest build(
        String sessionId,
        LivingEntity target,
        DamageSource damageSource,
        float damageAmount,
        float victimHealthBefore,
        float victimHealthAfter
    ) {
        Entity attacker = damageSource.getEntity();
        AttackedDetails details = new AttackedDetails(
            EventRequestEntityMapper.toWeapon(attacker),
            damageAmount,
            victimHealthBefore,
            victimHealthAfter
        );

        return EventRequestAssembler.build(
            sessionId,
            target.level().getGameTime(),
            toSubject(attacker, target),
            EventVerb.ATTACKED,
            details,
            EventRequestEntityMapper.toEntity(target)
        );
    }

    private static EventRequest.EntityRequest toSubject(Entity attacker, LivingEntity target) {
        if (attacker == null) {
            return EventRequestEntityMapper.toEnvironment(target);
        }
        return EventRequestEntityMapper.toEntity(attacker);
    }
}

