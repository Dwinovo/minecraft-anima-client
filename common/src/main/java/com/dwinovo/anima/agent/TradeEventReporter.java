package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TradeEventReporter {

    private static final String TRADED_VERB = "minecraft.traded";
    private static final long REPORT_COOLDOWN_TICKS = 2L;
    private static final long COOLDOWN_RETENTION_TICKS = 400L;
    private static final long CLEANUP_INTERVAL_TICKS = 100L;

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-trade-event-reporter");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<TradeKey, Long> LAST_REPORT_TICKS = new ConcurrentHashMap<>();

    private TradeEventReporter() {
    }

    public static void onTradeCompleted(AbstractVillager merchant, MerchantOffer offer) {
        if (merchant.level() == null || merchant.level().isClientSide()) {
            return;
        }
        if (offer == null) {
            return;
        }

        Player trader = merchant.getTradingPlayer();
        if (trader == null) {
            return;
        }

        UUID traderUuid = trader.getUUID();
        UUID merchantUuid = merchant.getUUID();
        if (traderUuid.equals(merchantUuid)) {
            return;
        }

        long worldTime = Math.max(0L, merchant.level().getGameTime());
        if (worldTime % CLEANUP_INTERVAL_TICKS == 0L) {
            cleanupCooldown(worldTime);
        }

        ItemStack resultStack = offer.getResult();
        String resultItemId = resolveItemId(resultStack);
        int offerUses = Math.max(0, offer.getUses());
        TradeKey tradeKey = new TradeKey(traderUuid, merchantUuid, resultItemId, offerUses);
        Long lastReportTick = LAST_REPORT_TICKS.get(tradeKey);
        if (lastReportTick != null && worldTime - lastReportTick < REPORT_COOLDOWN_TICKS) {
            return;
        }
        LAST_REPORT_TICKS.put(tradeKey, worldTime);

        EntityLifecycle.EntityCredential traderCredential = EntityLifecycle.findEntityCredential(traderUuid);
        EntityLifecycle.EntityCredential merchantCredential = EntityLifecycle.findEntityCredential(merchantUuid);
        if (traderCredential == null && merchantCredential == null) {
            return;
        }

        boolean subjectIsTrader = traderCredential != null;
        EntityLifecycle.EntityCredential subjectCredential = subjectIsTrader ? traderCredential : merchantCredential;
        UUID targetUuid = subjectIsTrader ? merchantUuid : traderUuid;
        EntityLifecycle.EntityCredential targetCredential = subjectIsTrader ? merchantCredential : traderCredential;

        if (targetCredential != null && !subjectCredential.sessionId().equals(targetCredential.sessionId())) {
            targetCredential = null;
        }

        String targetRef = resolveTargetRef(subjectCredential, targetCredential, targetUuid);
        JsonObject details = buildDetails(merchant, trader, offer, resultStack, resultItemId, subjectIsTrader);
        REPORT_EXECUTOR.execute(() -> reportTradeEvent(subjectCredential, targetRef, worldTime, details));
    }

    private static JsonObject buildDetails(
            AbstractVillager merchant,
            Player trader,
            MerchantOffer offer,
            ItemStack resultStack,
            String resultItemId,
            boolean subjectIsTrader
    ) {
        ItemStack costA = offer.getCostA();
        ItemStack costB = offer.getCostB();

        JsonObject details = new JsonObject();
        details.addProperty("event_type", "TRADED");
        details.addProperty("subject_role", subjectIsTrader ? "trader" : "merchant");
        details.addProperty("trader_entity_uuid", trader.getUUID().toString());
        details.addProperty("trader_name", resolveDisplayName(trader));
        details.addProperty("merchant_entity_uuid", merchant.getUUID().toString());
        details.addProperty("merchant_name", resolveDisplayName(merchant));
        details.addProperty("merchant_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(merchant.getType()).toString());
        details.addProperty("cost_a_item_id", resolveItemId(costA));
        details.addProperty("cost_a_count", costA.getCount());
        details.addProperty("cost_b_item_id", resolveItemId(costB));
        details.addProperty("cost_b_count", costB.isEmpty() ? 0 : costB.getCount());
        details.addProperty("result_item_id", resultItemId);
        details.addProperty("result_item_name", resultStack.getHoverName().getString());
        details.addProperty("result_count", resultStack.getCount());
        details.addProperty("offer_uses", offer.getUses());
        details.addProperty("offer_max_uses", offer.getMaxUses());
        details.addProperty("offer_xp", offer.getXp());
        details.addProperty("offer_demand", offer.getDemand());
        details.addProperty("offer_price_multiplier", offer.getPriceMultiplier());
        details.addProperty("offer_reward_exp", offer.shouldRewardExp());

        if (merchant instanceof Villager villager) {
            details.addProperty(
                    "merchant_profession",
                    BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).toString()
            );
            details.addProperty("merchant_level", villager.getVillagerData().getLevel());
        }

        return details;
    }

    private static void reportTradeEvent(
            EntityLifecycle.EntityCredential subjectCredential,
            String targetRef,
            long worldTime,
            JsonObject details
    ) {
        try {
            API_CLIENT.reportEvent(
                    subjectCredential.sessionId(),
                    subjectCredential.entityId(),
                    subjectCredential.accessToken(),
                    TRADED_VERB,
                    targetRef,
                    details,
                    worldTime
            );
            Constants.LOG.info(
                    "[Anima-Trade] reported: subject={} target_ref={} verb={} result={} count={}",
                    subjectCredential.displayName(),
                    targetRef,
                    TRADED_VERB,
                    details.get("result_item_id").getAsString(),
                    details.get("result_count").getAsInt()
            );
        } catch (Exception exception) {
            Constants.LOG.warn(
                    "[Anima-Trade] failed to report: subject={} target_ref={} verb={}",
                    subjectCredential.displayName(),
                    targetRef,
                    TRADED_VERB,
                    exception
            );
        }
    }

    private static String resolveTargetRef(
            EntityLifecycle.EntityCredential subjectCredential,
            EntityLifecycle.EntityCredential targetCredential,
            UUID targetUuid
    ) {
        if (targetCredential != null && subjectCredential.sessionId().equals(targetCredential.sessionId())) {
            return "entity:" + targetCredential.entityId();
        }
        return "entity:" + targetUuid;
    }

    private static String resolveDisplayName(Entity entity) {
        String name = entity.getName().getString().trim();
        if (!name.isEmpty()) {
            return name;
        }
        return entity.getType().getDescription().getString();
    }

    private static String resolveItemId(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return "minecraft:air";
        }
        return BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
    }

    private static void cleanupCooldown(long worldTime) {
        long minTick = worldTime - COOLDOWN_RETENTION_TICKS;
        for (Map.Entry<TradeKey, Long> entry : LAST_REPORT_TICKS.entrySet()) {
            if (entry.getValue() >= minTick) {
                continue;
            }
            LAST_REPORT_TICKS.remove(entry.getKey(), entry.getValue());
        }
    }

    private record TradeKey(UUID traderUuid, UUID merchantUuid, String resultItemId, int offerUses) {
    }
}
