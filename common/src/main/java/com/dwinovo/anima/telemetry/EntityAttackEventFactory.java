package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventRequest;
import net.minecraft.core.registries.BuiltInRegistries;
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
        float damageAmount
    ) {
        long now = System.currentTimeMillis();
        Entity attacker = damageSource.getEntity();
        String attackerName = attacker == null ? "环境" : attacker.getName().getString();
        String targetName = target.getName().getString();
        String damageType = damageSource.type().msgId();

        return new EventRequest(
            new EventRequest.MetaRequest(sessionId),
            new EventRequest.WhenRequest(
                Instant.ofEpochMilli(now).toString(),
                now,
                target.level().getGameTime()
            ),
            new EventRequest.WhereRequest(
                target.level().dimension().location().toString(),
                target.getX(),
                target.getY(),
                target.getZ()
            ),
            new EventRequest.WhoRequest(
                target.getUUID().toString(),
                target.getName().getString(),
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString()
            ),
            new EventRequest.EventBodyRequest(
                toActor(attacker),
                "entity_attacked",
                toActor(target),
                new EventRequest.DetailsRequest(
                    damageType,
                    damageAmount,
                    toEntityType(attacker)
                ),
                String.format("%s 对 %s 造成了 %.2f 点伤害（伤害类型：%s）。", attackerName, targetName, damageAmount, damageType)
            )
        );
    }

    private static EventRequest.ActorRequest toActor(Entity entity) {
        if (entity == null) {
            return new EventRequest.ActorRequest(null, null, null);
        }
        return new EventRequest.ActorRequest(
            entity.getUUID().toString(),
            entity.getName().getString(),
            toEntityType(entity)
        );
    }

    private static String toEntityType(Entity entity) {
        if (entity == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
