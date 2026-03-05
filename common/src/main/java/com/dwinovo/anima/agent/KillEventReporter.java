package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class KillEventReporter {

    private static final String KILL_EVENT_VERB = "minecraft.entity_killed";
    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-kill-event-reporter");
        thread.setDaemon(true);
        return thread;
    });

    private KillEventReporter() {
    }

    public static void onLivingEntityDied(LivingEntity victim, DamageSource damageSource) {
        if (victim.level() == null) {
            Constants.LOG.info(
                    "[Anima-KillHook] ignored: victim='{}' uuid={} has null level",
                    victim.getName().getString(),
                    victim.getUUID()
            );
            return;
        }
        if (victim.level().isClientSide()) {
            Constants.LOG.info(
                    "[Anima-KillHook] ignored: victim='{}' uuid={} on client side (server-only reporting)",
                    victim.getName().getString(),
                    victim.getUUID()
            );
            return;
        }

        Entity attackerEntity = damageSource.getEntity();
        if (attackerEntity == null) {
            Constants.LOG.info(
                    "[Anima-KillHook] ignored: victim='{}' uuid={} DamageSource#getEntity returned null (source={}, side=server)",
                    victim.getName().getString(),
                    victim.getUUID(),
                    damageSource.getMsgId()
            );
            return;
        }
        Constants.LOG.info(
                "[Anima-KillHook] attacker resolved by DamageSource#getEntity: attacker='{}' uuid={} source={} side=server",
                attackerEntity.getName().getString(),
                attackerEntity.getUUID(),
                damageSource.getMsgId()
        );

        UUID attackerEntityId = attackerEntity.getUUID();
        EntityLifecycle.EntityCredential killerAgent = EntityLifecycle.findEntityCredential(attackerEntityId);
        if (killerAgent == null) {
            Constants.LOG.info(
                    "[Anima-KillHook] ignored: attacker='{}' uuid={} is not registered as Anima entity",
                    attackerEntity.getName().getString(),
                    attackerEntityId
            );
            return;
        }

        EntityLifecycle.EntityCredential victimAgent = EntityLifecycle.findEntityCredential(victim.getUUID());
        String targetRef = resolveTargetRef(victim, victimAgent);
        long worldTime = Math.max(0L, victim.level().getGameTime());
        JsonObject details = buildDetails(attackerEntity, victim, damageSource, victimAgent);

        Constants.LOG.info(
                "[Anima-KillHook] enqueue report: killer='{}' killerAgent={} victim='{}' target_ref={} world_time={}",
                killerAgent.displayName(),
                killerAgent.entityId(),
                victim.getName().getString(),
                targetRef,
                worldTime
        );
        REPORT_EXECUTOR.execute(() -> reportKilledEvent(killerAgent, targetRef, worldTime, details));
    }

    private static JsonObject buildDetails(
            Entity attackerEntity,
            LivingEntity victim,
            DamageSource damageSource,
            EntityLifecycle.EntityCredential victimAgent
    ) {
        JsonObject details = new JsonObject();
        details.addProperty("event_type", "ENTITY_KILLED");
        details.addProperty("killer_entity_uuid", attackerEntity.getUUID().toString());
        details.addProperty("killer_name", resolveDisplayName(attackerEntity));
        details.addProperty("victim_entity_uuid", victim.getUUID().toString());
        details.addProperty("victim_name", resolveDisplayName(victim));
        details.addProperty("killer_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(attackerEntity.getType()).toString());
        details.addProperty("damage_source", damageSource.getMsgId());
        details.addProperty("death_reason", damageSource.getMsgId());
        details.addProperty("death_message", damageSource.getLocalizedDeathMessage(victim).getString());
        addWeaponDetails(attackerEntity, details);
        addDirectCauseDetails(damageSource, details);
        if (victimAgent != null) {
            details.addProperty("victim_entity_id", victimAgent.entityId());
            details.addProperty("victim_display_name", victimAgent.displayName());
        }
        return details;
    }

    private static String resolveTargetRef(LivingEntity victim, EntityLifecycle.EntityCredential victimAgent) {
        if (victimAgent != null) {
            return "entity:" + victimAgent.entityId();
        }
        return "entity:" + victim.getUUID();
    }

    private static String resolveDisplayName(Entity entity) {
        String name = entity.getName().getString().trim();
        if (!name.isEmpty()) {
            return name;
        }
        return entity.getType().getDescription().getString();
    }

    private static void addWeaponDetails(Entity attackerEntity, JsonObject details) {
        if (!(attackerEntity instanceof LivingEntity livingAttacker)) {
            details.addProperty("killer_weapon_id", "none");
            details.addProperty("killer_weapon_name", "none");
            return;
        }
        ItemStack mainHand = livingAttacker.getMainHandItem();
        if (mainHand.isEmpty()) {
            details.addProperty("killer_weapon_id", "minecraft:air");
            details.addProperty("killer_weapon_name", "air");
            return;
        }
        details.addProperty("killer_weapon_id", BuiltInRegistries.ITEM.getKey(mainHand.getItem()).toString());
        details.addProperty("killer_weapon_name", mainHand.getHoverName().getString());
    }

    private static void addDirectCauseDetails(DamageSource damageSource, JsonObject details) {
        Entity directEntity = damageSource.getDirectEntity();
        if (directEntity == null) {
            details.addProperty("damage_direct_entity_uuid", "none");
            details.addProperty("damage_direct_entity_type", "none");
            details.addProperty("damage_direct_entity_name", "none");
            return;
        }
        details.addProperty("damage_direct_entity_uuid", directEntity.getUUID().toString());
        details.addProperty("damage_direct_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(directEntity.getType()).toString());
        details.addProperty("damage_direct_entity_name", resolveDisplayName(directEntity));
    }

    private static void reportKilledEvent(
            EntityLifecycle.EntityCredential killerAgent,
            String targetRef,
            long worldTime,
            JsonObject details
    ) {
        try {
            API_CLIENT.reportEvent(
                    killerAgent.sessionId(),
                    killerAgent.entityId(),
                    killerAgent.accessToken(),
                    KILL_EVENT_VERB,
                    targetRef,
                    details,
                    worldTime
            );
            Constants.LOG.info(
                    "Reported kill event as {}: killer={}, target_ref={}",
                    KILL_EVENT_VERB,
                    killerAgent.displayName(),
                    targetRef
            );
        } catch (Exception exception) {
            Constants.LOG.warn(
                    "Failed to report kill {} event for killer {} ({})",
                    KILL_EVENT_VERB,
                    killerAgent.displayName(),
                    killerAgent.entityId(),
                    exception
            );
        }
    }
}
