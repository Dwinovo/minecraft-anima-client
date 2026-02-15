package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.GameEventEnvelope;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public final class PlayerDeathTelemetryReporter {

    private PlayerDeathTelemetryReporter() {}

    public static void report(ServerPlayer player, DamageSource damageSource) {
        GameEventEnvelope payload = PlayerDeathEventFactory.build(player, damageSource);
        TelemetryHttpClient.post("/step", payload, "player-death");
    }
}
