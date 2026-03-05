package com.dwinovo.anima.mixin;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.agent.AssistEventReporter;
import com.dwinovo.anima.agent.ConflictEventReporter;
import com.dwinovo.anima.agent.HealedEventReporter;
import com.dwinovo.anima.agent.KillEventReporter;
import com.dwinovo.anima.agent.ProtectedEventReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @Inject(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("RETURN"),
            require = 0
    )
    private void onHurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        LivingEntity victim = (LivingEntity) (Object) this;
        if (victim.level() == null || victim.level().isClientSide()) {
            return;
        }
        AssistEventReporter.onEntityDamaged(victim, damageSource, amount);
        ProtectedEventReporter.onEntityDamaged(victim, damageSource, amount);
        ConflictEventReporter.onEntityDamaged(victim, damageSource, amount);
    }

    @Inject(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"),
            require = 0
    )
    private void onAddEffect(MobEffectInstance effectInstance, Entity sourceEntity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        LivingEntity target = (LivingEntity) (Object) this;
        if (target.level() == null || target.level().isClientSide()) {
            return;
        }
        if (sourceEntity == null) {
            return;
        }
        HealedEventReporter.onBeneficialEffectApplied(target, sourceEntity, effectInstance);
    }

    @Inject(
            method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void onDie(DamageSource damageSource, CallbackInfo callbackInfo) {
        LivingEntity victim = (LivingEntity) (Object) this;
        if (victim.level() == null || victim.level().isClientSide()) {
            return;
        }
        Constants.LOG.info(
                "[Anima-KillHook] captured die(server): victim='{}' uuid={} source={}",
                victim.getName().getString(),
                victim.getUUID(),
                damageSource.getMsgId()
        );
        AssistEventReporter.onEntityDied(victim, damageSource);
        ConflictEventReporter.onEntityDied(victim, damageSource);
        KillEventReporter.onLivingEntityDied(victim, damageSource);
    }
}
