package com.dwinovo.anima;


import com.dwinovo.anima.command.AnimaCommand;
import com.dwinovo.anima.entity.AnimaEntityProfileLogger;
import com.dwinovo.anima.telemetry.agent.AnimaAgentLoadHandler;
import com.dwinovo.anima.telemetry.agent.AnimaAgentUnloadHandler;
import com.dwinovo.anima.telemetry.event.attack.EntityAttackTelemetryReporter;
import com.dwinovo.anima.telemetry.event.death.DeathEventTelemetryReporter;
import com.dwinovo.anima.telemetry.session.SessionRegistrationService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.server.level.ServerPlayer;

@Mod(Constants.MOD_ID)
public class AnimaMod {

    public AnimaMod(IEventBus eventBus) {

        // This method is invoked by the NeoForge mod loader when it is ready
        // to load your mod. You can access NeoForge and Common code in this
        // project.

        // Use NeoForge to bootstrap the Common mod.
        Constants.LOG.info("Hello NeoForge world!");
        CommonClass.init();

        NeoForge.EVENT_BUS.addListener(this::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(this::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        EntityAttackTelemetryReporter.reportIfSupported(
            event.getEntity(),
            event.getSource(),
            event.getAmount(),
            "neoforge-incoming-damage",
            true
        );
    }

    private void onLivingDeath(LivingDeathEvent event) {
        DeathEventTelemetryReporter.reportLivingDeath(
            event.getEntity(),
            event.getSource(),
            "neoforge-living-death"
        );
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SessionRegistrationService.registerOnWorldLoad(player, "neoforge-login");
        }
    }

    private void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            AnimaEntityProfileLogger.logProfileIfSupported(event.getEntity());
            AnimaAgentLoadHandler.onEntityLoaded(event.getEntity(), "neoforge-entity-load");
        }
    }

    private void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        AnimaAgentUnloadHandler.onEntityUnloaded(event.getEntity(), "neoforge-entity-unload");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AnimaCommand.register(event.getDispatcher());
    }
}


