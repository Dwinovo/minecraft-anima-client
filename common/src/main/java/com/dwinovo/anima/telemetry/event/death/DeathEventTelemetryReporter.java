package com.dwinovo.anima.telemetry.event.death;

import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.event.core.EventUploadReporter;
import com.dwinovo.anima.telemetry.event.helped.HelpedEventTelemetryReporter;
import com.dwinovo.anima.telemetry.session.SessionRegistrationService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class DeathEventTelemetryReporter {

    private static final EventUploadReporter EVENT_UPLOADER = new EventUploadReporter();

    private DeathEventTelemetryReporter() {}

    public static void reportLivingDeath(
        LivingEntity victim,
        DamageSource damageSource,
        String source
    ) {
        if (victim.level().isClientSide()) {
            return;
        }

        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return;
        }

        String sessionId = SessionRegistrationService.getOrCreateSessionId(server);
        EventRequest payload = DeathEventFactory.buildLivingDeathEvent(sessionId, victim, damageSource);
        HelpedEventTelemetryReporter.reportDeath(victim, damageSource, source, sessionId);
        uploadEvent(sessionId, payload, source, victim.getUUID().toString());
    }

    private static void uploadEvent(
        String sessionId,
        EventRequest payload,
        String source,
        String subjectUuid
    ) {
        EVENT_UPLOADER.upload(sessionId, payload, source, "Death event", "subject_uuid", subjectUuid);
    }
}

