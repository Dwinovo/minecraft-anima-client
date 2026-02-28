package com.dwinovo.anima.client;

import com.dwinovo.anima.client.feed.SessionFeedScreen;
import com.dwinovo.anima.telemetry.session.SessionRegistrationService;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.MinecraftServer;
import org.lwjgl.glfw.GLFW;

public final class AnimaFabricClient implements ClientModInitializer {

    private static final KeyMapping OPEN_FEED_KEY = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.anima.open_feed", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.anima")
    );

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_FEED_KEY.consumeClick()) {
                toggleFeedScreen(client);
            }
        });
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
