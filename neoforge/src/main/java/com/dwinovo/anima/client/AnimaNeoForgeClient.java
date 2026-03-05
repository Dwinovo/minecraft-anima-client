package com.dwinovo.anima.client;

import com.dwinovo.anima.agent.EntityLifecycle;
import com.dwinovo.anima.ui.SessionJoinFragment;
import com.mojang.blaze3d.platform.InputConstants;
import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.lwjgl.glfw.GLFW;

public final class AnimaNeoForgeClient {

    private static final KeyMapping OPEN_SESSION_UI_KEY = new KeyMapping(
            "key.anima.open_session",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.anima"
    );

    private AnimaNeoForgeClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(AnimaNeoForgeClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(AnimaNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(AnimaNeoForgeClient::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(AnimaNeoForgeClient::onEntityLeaveLevel);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SESSION_UI_KEY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
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
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        EntityLifecycle.onEntityLoaded(event.getEntity());
    }

    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        EntityLifecycle.onEntityUnloaded(event.getEntity());
    }
}
