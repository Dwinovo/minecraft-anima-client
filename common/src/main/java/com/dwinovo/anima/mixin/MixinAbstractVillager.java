package com.dwinovo.anima.mixin;

import com.dwinovo.anima.agent.TradeEventReporter;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractVillager.class)
public class MixinAbstractVillager {

    @Inject(
            method = "notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void onNotifyTrade(MerchantOffer offer, CallbackInfo callbackInfo) {
        AbstractVillager merchant = (AbstractVillager) (Object) this;
        TradeEventReporter.onTradeCompleted(merchant, offer);
    }
}
