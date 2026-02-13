package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.telemetry.model.GameEventEnvelope;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public final class PlayerDeathTelemetryReporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PlayerDeathTelemetryReporter() {}

    public static void report(ServerPlayer player, DamageSource damageSource) {
        GameEventEnvelope payload = PlayerDeathEventFactory.build(player, damageSource);
        Constants.LOG.info("Player death payload:\n{}", GSON.toJson(payload));
    }
}
