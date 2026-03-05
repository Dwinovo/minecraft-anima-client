package com.dwinovo.anima.mixin;

import com.dwinovo.anima.agent.BredEventReporter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public class MixinAnimalBreeding {

    @Inject(
            method = "finalizeSpawnChildFromBreeding(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;Lnet/minecraft/world/entity/AgeableMob;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void onFinalizeSpawnChildFromBreeding(
            ServerLevel serverLevel,
            Animal otherParent,
            AgeableMob child,
            CallbackInfo callbackInfo
    ) {
        Animal self = (Animal) (Object) this;
        BredEventReporter.onAnimalBred(self, otherParent, child, "animal.finalize_spawn_child_from_breeding");
    }
}
