package com.dwinovo.anima.mixin;

import com.dwinovo.anima.agent.TamedEventReporter;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
public class MixinAbstractHorseTame {

    @Inject(
            method = "tameWithName(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("RETURN"),
            require = 0
    )
    private void onTameWithName(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        AbstractHorse horse = (AbstractHorse) (Object) this;
        if (!horse.isTamed()) {
            return;
        }
        TamedEventReporter.onEntityTamed(horse, player, "abstract_horse.tame_with_name");
    }
}
