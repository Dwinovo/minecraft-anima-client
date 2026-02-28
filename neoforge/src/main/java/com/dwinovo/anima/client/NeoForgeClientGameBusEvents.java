package com.dwinovo.anima.client;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.client.feed.SessionFeedScreen;
import com.dwinovo.anima.telemetry.session.SessionRegistrationService;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class NeoForgeClientGameBusEvents {

    private NeoForgeClientGameBusEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        while (NeoForgeClientBindings.OPEN_FEED_KEY.consumeClick()) {
            toggleFeedScreen(client);
        }
    }

    private static void toggleFeedScreen(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (client.screen instanceof SessionFeedScreen) {
            client.setScreen(null);
            return;
        }

        MinecraftServer server = client.getSingleplayerServer();
        String sessionId = server == null ? null : SessionRegistrationService.peekCachedSessionId(server);
        client.setScreen(new SessionFeedScreen(sessionId));
    }
}
