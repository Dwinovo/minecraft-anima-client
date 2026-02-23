package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventVerb;
import com.dwinovo.anima.telemetry.model.details.AttackedDetails;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.time.Instant;

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

        return new EventRequest(
            sessionId,
            target.level().getGameTime(),
            Instant.now().toString(),
            toSubject(attacker, target),
            new EventRequest.ActionRequest(EventVerb.ATTACKED.value(), details.toMap()),
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
