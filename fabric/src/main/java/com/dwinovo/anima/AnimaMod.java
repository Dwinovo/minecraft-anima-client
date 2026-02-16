package com.dwinovo.anima;

import com.dwinovo.anima.telemetry.PlayerDeathTelemetryReporter;
import com.dwinovo.anima.telemetry.SessionRegistrationService;
import com.dwinovo.anima.entity.AnimaEntityProfileLogger;
import com.dwinovo.anima.registry.FabricEntityRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public class AnimaMod implements ModInitializer {
    
    @Override
    public void onInitialize() {
        
        // This method is invoked by the Fabric mod loader when it is ready
        // to load your mod. You can access Fabric and Common code in this
        // project.

        // Use Fabric to bootstrap the Common mod.
        Constants.LOG.info("Hello Fabric world!");
        CommonClass.init();
        FabricEntityRegistry.init();

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                PlayerDeathTelemetryReporter.report(player, damageSource);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SessionRegistrationService.registerOnWorldLoad(handler.player, "fabric-login");
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            AnimaEntityProfileLogger.logProfileIfSupported(entity);
        });
    }
}
