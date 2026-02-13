package com.dwinovo.anima;


import com.dwinovo.anima.telemetry.PlayerDeathTelemetryReporter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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

        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
    }

    private void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDeathTelemetryReporter.report(player, event.getSource());
        }
    }
}
