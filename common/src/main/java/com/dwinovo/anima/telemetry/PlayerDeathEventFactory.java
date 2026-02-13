package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.GameEventEnvelope;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.time.Instant;

public final class PlayerDeathEventFactory {

    private PlayerDeathEventFactory() {}

    public static GameEventEnvelope build(ServerPlayer deadPlayer, DamageSource damageSource) {
        long now = System.currentTimeMillis();

        GameEventEnvelope payload = new GameEventEnvelope();
        payload.meta = new GameEventEnvelope.Meta("replace-with-auth-token", "1.0");
        payload.when = new GameEventEnvelope.When(Instant.ofEpochMilli(now).toString(), now, deadPlayer.level().getGameTime());
        payload.where = new GameEventEnvelope.Where(
            deadPlayer.level().dimension().location().toString(),
            deadPlayer.getX(),
            deadPlayer.getY(),
            deadPlayer.getZ()
        );
        payload.who = new GameEventEnvelope.Who(
            deadPlayer.getUUID().toString(),
            deadPlayer.getGameProfile().getName(),
            "first_person"
        );

        Entity sourceEntity = damageSource.getEntity();
        GameEventEnvelope.Actor subject = toActor(sourceEntity);
        GameEventEnvelope.Actor object = new GameEventEnvelope.Actor(
            deadPlayer.getUUID().toString(),
            deadPlayer.getGameProfile().getName(),
            "minecraft:player"
        );
        GameEventEnvelope.Details details = new GameEventEnvelope.Details(
            damageSource.type().msgId(),
            toEntityType(sourceEntity),
            resolveDeathMessage(deadPlayer)
        );
        payload.event = new GameEventEnvelope.Event(subject, "player_death", object, details);

        return payload;
    }

    private static String resolveDeathMessage(ServerPlayer player) {
        Component deathMessage = player.getCombatTracker().getDeathMessage();
        return deathMessage == null ? null : deathMessage.getString();
    }

    private static GameEventEnvelope.Actor toActor(Entity entity) {
        if (entity == null) {
            return new GameEventEnvelope.Actor(null, null, null);
        }
        String id = entity.getUUID().toString();
        String name = entity.getName().getString();
        String type = toEntityType(entity);
        return new GameEventEnvelope.Actor(id, name, type);
    }

    private static String toEntityType(Entity entity) {
        if (entity == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
