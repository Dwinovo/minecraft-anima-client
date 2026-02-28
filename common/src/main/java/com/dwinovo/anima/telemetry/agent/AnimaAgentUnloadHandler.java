package com.dwinovo.anima.telemetry.agent;

import net.minecraft.world.entity.Entity;

public final class AnimaAgentUnloadHandler {

    private AnimaAgentUnloadHandler() {}

    public static void onEntityUnloaded(Entity entity, String source) {
        AnimaAgentLifecycleService.deactivateOnEntityUnloaded(entity, source);
    }
}

