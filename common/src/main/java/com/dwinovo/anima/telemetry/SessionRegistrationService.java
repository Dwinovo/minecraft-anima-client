package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class SessionRegistrationService {

    private static final Set<MinecraftServer> REGISTERED_SERVERS = Collections.newSetFromMap(new WeakHashMap<>());

    private SessionRegistrationService() {}

    public static void registerOnWorldLoad(ServerPlayer player, String source) {
        MinecraftServer server = player.serverLevel().getServer();
        synchronized (REGISTERED_SERVERS) {
            if (!REGISTERED_SERVERS.add(server)) {
                return;
            }
        }

        ServerLevel overworld = server.overworld();
        SessionSavedData sessionData = overworld.getDataStorage().computeIfAbsent(SessionSavedData.factory(), SessionSavedData.DATA_NAME);
        boolean generated = !sessionData.hasSessionId();
        String sessionId = sessionData.getOrCreateSessionId();
        String seed = Long.toString(overworld.getSeed());
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("anima_session_id", sessionId);
        requestJson.addProperty("seed", seed);

        Constants.LOG.info("[{}] Using anima_session_id={}, generated={}, seed={}", source, sessionId, generated, seed);
        TelemetryHttpClient.post("/api/session/register", requestJson, source);
    }
}
