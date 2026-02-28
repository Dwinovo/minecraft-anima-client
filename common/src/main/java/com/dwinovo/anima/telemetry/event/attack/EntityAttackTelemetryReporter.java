package com.dwinovo.anima.telemetry.event.attack;

import com.dwinovo.anima.entity.IAnimaEntity;
import com.dwinovo.anima.telemetry.event.core.EventUploadReporter;
import com.dwinovo.anima.telemetry.event.helped.HelpedEventTelemetryReporter;
import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.session.SessionRegistrationService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class EntityAttackTelemetryReporter {

    private static final EventUploadReporter EVENT_UPLOADER = new EventUploadReporter();

    private EntityAttackTelemetryReporter() {}

    public static void reportIfSupported(
        LivingEntity target,
        DamageSource damageSource,
        float damageAmount,
        String source
    ) {
        reportIfSupported(target, damageSource, damageAmount, source, false);
    }

    public static void reportIfSupported(
        LivingEntity target,
        DamageSource damageSource,
        float damageAmount,
        String source,
        boolean healthSnapshotBeforeDamage
    ) {
        if (target.level().isClientSide() || !(target instanceof IAnimaEntity)) {
            return;
        }

        MinecraftServer server = target.level().getServer();
        if (server == null) {
            return;
        }

        float currentHealth = target.getHealth();
        float maxHealth = target.getMaxHealth();
        float victimHealthBefore = healthSnapshotBeforeDamage
            ? clampHealth(currentHealth, maxHealth)
            : clampHealth(currentHealth + damageAmount, maxHealth);
        float victimHealthAfter = healthSnapshotBeforeDamage
            ? clampHealth(currentHealth - damageAmount, maxHealth)
            : clampHealth(currentHealth, maxHealth);

        String sessionId = SessionRegistrationService.getOrCreateSessionId(server);
        EventRequest payload = EntityAttackEventFactory.build(
            sessionId,
            target,
            damageSource,
            damageAmount,
            victimHealthBefore,
            victimHealthAfter
        );
        HelpedEventTelemetryReporter.reportAttack(
            target,
            damageSource,
            damageAmount,
            source,
            sessionId
        );
        EVENT_UPLOADER.upload(sessionId, payload, source, "Event", "entity_uuid", target.getUUID().toString());
    }

    private static float clampHealth(float health, float maxHealth) {
        if (health <= 0.0F) {
            return 0.0F;
        }
        return Math.min(health, maxHealth);
    }
}

