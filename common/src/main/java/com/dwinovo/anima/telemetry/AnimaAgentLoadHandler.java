package com.dwinovo.anima.telemetry;

import net.minecraft.world.entity.Entity;

public final class AnimaAgentLoadHandler {

    private AnimaAgentLoadHandler() {
    }

    public static void onEntityLoaded(Entity entity, String source) {
        AnimaAgentLifecycleService.activateOnEntityLoaded(entity, source);
    }
}
