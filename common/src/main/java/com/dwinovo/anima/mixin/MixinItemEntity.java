package com.dwinovo.anima.mixin;

import com.dwinovo.anima.agent.GiftEventReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class MixinItemEntity {

    @Unique
    private int anima$pickupCountBefore = 0;

    @Inject(
            method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void onPlayerTouchHead(Player player, CallbackInfo callbackInfo) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        anima$pickupCountBefore = itemEntity.getItem().getCount();
    }

    @Inject(
            method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void onPlayerTouchReturn(Player player, CallbackInfo callbackInfo) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        int afterCount = itemEntity.getItem().getCount();
        GiftEventReporter.onItemPickedUp(itemEntity, player, anima$pickupCountBefore, afterCount);
    }
}

