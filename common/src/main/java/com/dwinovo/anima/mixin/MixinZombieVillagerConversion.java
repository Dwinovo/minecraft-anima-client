package com.dwinovo.anima.mixin;

import com.dwinovo.anima.agent.CuredEventReporter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.UUID;

@Mixin(ZombieVillager.class)
public class MixinZombieVillagerConversion {

    @Shadow
    private UUID conversionStarter;

    @Inject(
            method = "finishConversion(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/world/entity/monster/ZombieVillager;convertTo(Lnet/minecraft/world/entity/EntityType;Z)Lnet/minecraft/world/entity/Mob;"
            ),
            locals = LocalCapture.CAPTURE_FAILSOFT,
            require = 0
    )
    private void onFinishConversion(ServerLevel serverLevel, CallbackInfo callbackInfo, Villager villager) {
        ZombieVillager zombieVillager = (ZombieVillager) (Object) this;
        CuredEventReporter.onZombieVillagerCured(zombieVillager, villager, conversionStarter);
    }
}
