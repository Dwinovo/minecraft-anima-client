package com.dwinovo.anima.client;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.registry.NeoForgeEntityRegistry;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NeoForgeClientEvents {

    private NeoForgeClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NeoForgeEntityRegistry.TEST_ENTITY.get(), CowRenderer::new);
    }
}
