package com.dwinovo.anima.telemetry;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.time.Instant;

public final class EntityAttackEventFactory {

    private EntityAttackEventFactory() {}

    public static JsonObject build(
        String sessionId,
        LivingEntity target,
        DamageSource damageSource,
        float damageAmount
    ) {
        long now = System.currentTimeMillis();
        Entity attacker = damageSource.getEntity();

        JsonObject payload = new JsonObject();
        payload.addProperty("session_id", sessionId);

        JsonObject when = new JsonObject();
        when.addProperty("iso8601", Instant.ofEpochMilli(now).toString());
        when.addProperty("epoch_millis", now);
        when.addProperty("game_time", target.level().getGameTime());
        payload.add("when", when);

        JsonObject where = new JsonObject();
        where.addProperty("dimension", target.level().dimension().location().toString());
        where.addProperty("x", target.getX());
        where.addProperty("y", target.getY());
        where.addProperty("z", target.getZ());
        payload.add("where", where);

        JsonObject who = new JsonObject();
        who.addProperty("entity_uuid", target.getUUID().toString());
        who.addProperty("entity_name", target.getName().getString());
        who.addProperty("entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
        who.addProperty("perspective", "receiver");
        payload.add("who", who);

        JsonObject event = new JsonObject();
        event.add("subject", toActor(attacker));
        event.addProperty("action", "entity_attacked");
        event.add("object", toActor(target));

        JsonObject details = new JsonObject();
        details.addProperty("damage_type", damageSource.type().msgId());
        details.addProperty("damage_amount", damageAmount);
        details.addProperty("damage_source_entity_type", toEntityType(attacker));
        event.add("details", details);

        payload.add("event", event);
        return payload;
    }

    private static JsonObject toActor(Entity entity) {
        JsonObject actor = new JsonObject();
        if (entity == null) {
            actor.add("id", JsonNull.INSTANCE);
            actor.add("name", JsonNull.INSTANCE);
            actor.add("type", JsonNull.INSTANCE);
            return actor;
        }
        actor.addProperty("id", entity.getUUID().toString());
        actor.addProperty("name", entity.getName().getString());
        actor.addProperty("type", toEntityType(entity));
        return actor;
    }

    private static String toEntityType(Entity entity) {
        if (entity == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
