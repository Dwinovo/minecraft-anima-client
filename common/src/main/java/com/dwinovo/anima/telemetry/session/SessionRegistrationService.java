package com.dwinovo.anima.telemetry.session;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.telemetry.api.AnimaApiClient;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class SessionRegistrationService {

    private static final Set<MinecraftServer> REGISTERED_SERVERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, String> SESSION_IDS = Collections.synchronizedMap(new WeakHashMap<>());

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
        SESSION_IDS.put(server, sessionId);
        AnimaApiClient.post("/api/sessions", requestJson, source);
    }

    public static String getOrCreateSessionId(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        SessionSavedData sessionData = overworld.getDataStorage().computeIfAbsent(SessionSavedData.factory(), SessionSavedData.DATA_NAME);
        String sessionId = sessionData.getOrCreateSessionId();
        SESSION_IDS.put(server, sessionId);
        return sessionId;
    }

    public static String peekCachedSessionId(MinecraftServer server) {
        return SESSION_IDS.get(server);
    }
}

