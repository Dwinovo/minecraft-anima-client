package com.dwinovo.anima.client;

import com.dwinovo.anima.agent.EntityLifecycle;
import com.dwinovo.anima.ui.SessionJoinFragment;
import com.mojang.blaze3d.platform.InputConstants;
import icyllis.modernui.mc.MuiModApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class AnimaFabricClient implements ClientModInitializer {

    private static final KeyMapping OPEN_SESSION_UI_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.anima.open_session",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_I,
                    "key.categories.anima"
            )
    );

    @Override
    public void onInitializeClient() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> EntityLifecycle.onEntityLoaded(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> EntityLifecycle.onEntityUnloaded(entity));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }

            if (EntityLifecycle.consumeWorldRescanRequest()) {
                EntityLifecycle.onEntityLoaded(client.player);
                if (client.level != null) {
                    for (var entity : client.level.entitiesForRendering()) {
                        EntityLifecycle.onEntityLoaded(entity);
                    }
                }
            }

            while (OPEN_SESSION_UI_KEY.consumeClick()) {
                var screen = MuiModApi.get().createScreen(
                        new SessionJoinFragment(),
                        null,
                        client.screen,
                        "Anima Session"
                );
                client.setScreen(screen);
            }
        });
    }
}
