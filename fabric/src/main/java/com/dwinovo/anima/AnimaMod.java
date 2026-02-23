package com.dwinovo.anima;

import com.dwinovo.anima.command.AnimaCommand;
import com.dwinovo.anima.telemetry.EntityAttackTelemetryReporter;
import com.dwinovo.anima.telemetry.SocialEventTelemetryReporter;
import com.dwinovo.anima.telemetry.SessionRegistrationService;
import com.dwinovo.anima.telemetry.AnimaAgentLoadHandler;
import com.dwinovo.anima.telemetry.AnimaAgentUnloadHandler;
import com.dwinovo.anima.entity.AnimaEntityProfileLogger;
import com.dwinovo.anima.registry.FabricEntityRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

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

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damageSource, baseDamageTaken, damageTaken, blocked) ->
            EntityAttackTelemetryReporter.reportIfSupported(
                entity,
                damageSource,
                damageTaken,
                "fabric-after-damage",
                false
            )
        );

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) ->
            SocialEventTelemetryReporter.reportLivingDeath(entity, damageSource, "fabric-after-death")
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SessionRegistrationService.registerOnWorldLoad(handler.player, "fabric-login");
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            AnimaEntityProfileLogger.logProfileIfSupported(entity);
            AnimaAgentLoadHandler.onEntityLoaded(entity, "fabric-entity-load");
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            AnimaAgentUnloadHandler.onEntityUnloaded(entity, "fabric-entity-unload");
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            AnimaCommand.register(dispatcher)
        );
    }
}
