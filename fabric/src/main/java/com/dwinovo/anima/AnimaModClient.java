package com.dwinovo.anima;

import com.dwinovo.anima.registry.FabricEntityRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.CowRenderer;

public class AnimaModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(FabricEntityRegistry.TEST_ENTITY, CowRenderer::new);
    }
}
