package com.dwinovo.anima.mixin;

import com.dwinovo.anima.agent.TamedEventReporter;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TamableAnimal.class)
public class MixinTamableAnimal {

    @Inject(
            method = "tame(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void onTame(Player player, CallbackInfo callbackInfo) {
        TamableAnimal tamableAnimal = (TamableAnimal) (Object) this;
        if (!tamableAnimal.isTame()) {
            return;
        }
        TamedEventReporter.onEntityTamed(tamableAnimal, player, "tamable_animal.tame");
    }
}
