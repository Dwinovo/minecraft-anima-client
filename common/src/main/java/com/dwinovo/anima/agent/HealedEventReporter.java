package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HealedEventReporter {

    private static final String HEALED_VERB = "minecraft.healed";
    private static final long REPORT_COOLDOWN_TICKS = 60L;
    private static final long COOLDOWN_RETENTION_TICKS = REPORT_COOLDOWN_TICKS * 10L;
    private static final long CLEANUP_INTERVAL_TICKS = 100L;

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-healed-event-reporter");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<HealedKey, Long> LAST_REPORT_TICKS = new ConcurrentHashMap<>();

    private HealedEventReporter() {
    }

    public static void onBeneficialEffectApplied(LivingEntity target, Entity sourceEntity, MobEffectInstance effectInstance) {
        if (target.level() == null || target.level().isClientSide()) {
            return;
        }
        if (!(sourceEntity instanceof LivingEntity healer)) {
            return;
        }

        UUID healerUuid = healer.getUUID();
        UUID targetUuid = target.getUUID();
        if (healerUuid.equals(targetUuid)) {
            return;
        }

        Holder<MobEffect> effectHolder = effectInstance.getEffect();
        MobEffect effect = effectHolder.value();
        if (!effect.isBeneficial()) {
            return;
        }

        long worldTime = Math.max(0L, target.level().getGameTime());
        if (worldTime % CLEANUP_INTERVAL_TICKS == 0L) {
            cleanupCooldown(worldTime);
        }

        String effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect).toString();
        HealedKey key = new HealedKey(healerUuid, targetUuid, effectId);
        Long lastReport = LAST_REPORT_TICKS.get(key);
        if (lastReport != null && worldTime - lastReport < REPORT_COOLDOWN_TICKS) {
            return;
        }

        EntityLifecycle.EntityCredential healerCredential = EntityLifecycle.findEntityCredential(healerUuid);
        if (healerCredential == null) {
            return;
        }
        EntityLifecycle.EntityCredential targetCredential = EntityLifecycle.findEntityCredential(targetUuid);
        String targetRef = resolveTargetRef(healerCredential, targetCredential, targetUuid);

        JsonObject details = buildDetails(target, healer, effectInstance, effect, effectId);
        LAST_REPORT_TICKS.put(key, worldTime);
        REPORT_EXECUTOR.execute(() -> reportHealedEvent(healerCredential, targetRef, worldTime, details));
    }

    private static String resolveTargetRef(
            EntityLifecycle.EntityCredential subjectCredential,
            EntityLifecycle.EntityCredential targetCredential,
            UUID targetUuid
    ) {
        if (targetCredential != null && subjectCredential.sessionId().equals(targetCredential.sessionId())) {
            return "entity:" + targetCredential.entityId();
        }
        return "entity:" + targetUuid;
    }

    private static JsonObject buildDetails(
            LivingEntity target,
            LivingEntity healer,
            MobEffectInstance effectInstance,
            MobEffect effect,
            String effectId
    ) {
        JsonObject details = new JsonObject();
        details.addProperty("event_type", "HEALED");
        details.addProperty("healer_entity_uuid", healer.getUUID().toString());
        details.addProperty("healer_name", resolveDisplayName(healer));
        details.addProperty("target_entity_uuid", target.getUUID().toString());
        details.addProperty("target_name", resolveDisplayName(target));
        details.addProperty("effect_id", effectId);
        details.addProperty("effect_name", effect.getDescriptionId());
        details.addProperty("effect_amplifier", effectInstance.getAmplifier());
        details.addProperty("effect_duration_ticks", effectInstance.getDuration());
        details.addProperty("effect_is_ambient", effectInstance.isAmbient());
        details.addProperty("effect_is_visible", effectInstance.isVisible());
        return details;
    }

    private static void reportHealedEvent(
            EntityLifecycle.EntityCredential healerCredential,
            String targetRef,
            long worldTime,
            JsonObject details
    ) {
        try {
            API_CLIENT.reportEvent(
                    healerCredential.sessionId(),
                    healerCredential.entityId(),
                    healerCredential.accessToken(),
                    HEALED_VERB,
                    targetRef,
                    details,
                    worldTime
            );
            Constants.LOG.info(
                    "[Anima-Healed] reported: subject={} target_ref={} verb={} effect={}",
                    healerCredential.displayName(),
                    targetRef,
                    HEALED_VERB,
                    details.get("effect_id").getAsString()
            );
        } catch (Exception exception) {
            Constants.LOG.warn(
                    "[Anima-Healed] failed to report: subject={} target_ref={} verb={}",
                    healerCredential.displayName(),
                    targetRef,
                    HEALED_VERB,
                    exception
            );
        }
    }

    private static void cleanupCooldown(long worldTime) {
        long minTick = worldTime - COOLDOWN_RETENTION_TICKS;
        for (Map.Entry<HealedKey, Long> entry : LAST_REPORT_TICKS.entrySet()) {
            if (entry.getValue() >= minTick) {
                continue;
            }
            LAST_REPORT_TICKS.remove(entry.getKey(), entry.getValue());
        }
    }

    private static String resolveDisplayName(LivingEntity entity) {
        String name = entity.getName().getString().trim();
        if (!name.isEmpty()) {
            return name;
        }
        return entity.getType().getDescription().getString();
    }

    private record HealedKey(UUID healerUuid, UUID targetUuid, String effectId) {
    }
}

