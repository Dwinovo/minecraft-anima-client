package com.dwinovo.anima.agent;

import com.dwinovo.anima.Constants;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GiftEventReporter {

    private static final String GIFT_VERB = "minecraft.gifted";
    private static final long MAX_PICKUP_LATENCY_TICKS = 100L;
    private static final long REPORT_COOLDOWN_TICKS = 40L;
    private static final long COOLDOWN_RETENTION_TICKS = 400L;
    private static final long CLEANUP_INTERVAL_TICKS = 100L;

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-gift-event-reporter");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<GiftKey, Long> LAST_REPORT_TICKS = new ConcurrentHashMap<>();

    private GiftEventReporter() {
    }

    public static void onItemPickedUp(ItemEntity itemEntity, LivingEntity receiver, int beforeCount, int afterCount) {
        if (itemEntity.level() == null || itemEntity.level().isClientSide()) {
            return;
        }
        if (afterCount >= beforeCount) {
            return;
        }

        Entity giverEntity = itemEntity.getOwner();
        if (giverEntity == null) {
            return;
        }
        UUID giverUuid = giverEntity.getUUID();

        UUID receiverUuid = receiver.getUUID();
        if (giverUuid.equals(receiverUuid)) {
            return;
        }

        long worldTime = Math.max(0L, itemEntity.level().getGameTime());
        if (worldTime % CLEANUP_INTERVAL_TICKS == 0L) {
            cleanupCooldown(worldTime);
        }

        int pickupAge = itemEntity.getAge();
        if (pickupAge < 0 || pickupAge > MAX_PICKUP_LATENCY_TICKS) {
            return;
        }

        EntityLifecycle.EntityCredential giverCredential = EntityLifecycle.findEntityCredential(giverUuid);
        EntityLifecycle.EntityCredential receiverCredential = EntityLifecycle.findEntityCredential(receiverUuid);
        if (giverCredential == null || receiverCredential == null) {
            return;
        }
        if (!giverCredential.sessionId().equals(receiverCredential.sessionId())) {
            return;
        }

        ItemStack itemStack = itemEntity.getItem();
        String itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();

        GiftKey giftKey = new GiftKey(giverUuid, receiverUuid, itemId);
        Long lastReportTick = LAST_REPORT_TICKS.get(giftKey);
        if (lastReportTick != null && worldTime - lastReportTick < REPORT_COOLDOWN_TICKS) {
            return;
        }
        LAST_REPORT_TICKS.put(giftKey, worldTime);

        int pickedCount = Math.max(1, beforeCount - afterCount);
        String targetRef = "entity:" + receiverCredential.entityId();
        JsonObject details = buildDetails(giverUuid, receiver, itemStack, itemId, pickedCount, pickupAge, beforeCount, afterCount);

        REPORT_EXECUTOR.execute(() -> reportGiftEvent(giverCredential, targetRef, worldTime, details));
    }

    private static JsonObject buildDetails(
            UUID giverUuid,
            LivingEntity receiver,
            ItemStack itemStack,
            String itemId,
            int pickedCount,
            int pickupAge,
            int beforeCount,
            int afterCount
    ) {
        JsonObject details = new JsonObject();
        details.addProperty("event_type", "GIFTED");
        details.addProperty("giver_entity_uuid", giverUuid.toString());
        details.addProperty("receiver_entity_uuid", receiver.getUUID().toString());
        details.addProperty("receiver_name", resolveDisplayName(receiver));
        details.addProperty("item_id", itemId);
        details.addProperty("item_name", itemStack.getHoverName().getString());
        details.addProperty("picked_count", pickedCount);
        details.addProperty("stack_count_before", beforeCount);
        details.addProperty("stack_count_after", afterCount);
        details.addProperty("pickup_latency_ticks", pickupAge);
        return details;
    }

    private static void reportGiftEvent(
            EntityLifecycle.EntityCredential giverCredential,
            String targetRef,
            long worldTime,
            JsonObject details
    ) {
        try {
            API_CLIENT.reportEvent(
                    giverCredential.sessionId(),
                    giverCredential.entityId(),
                    giverCredential.accessToken(),
                    GIFT_VERB,
                    targetRef,
                    details,
                    worldTime
            );
            Constants.LOG.info(
                    "[Anima-Gift] reported: subject={} target_ref={} verb={} item={} count={}",
                    giverCredential.displayName(),
                    targetRef,
                    GIFT_VERB,
                    details.get("item_id").getAsString(),
                    details.get("picked_count").getAsInt()
            );
        } catch (Exception exception) {
            Constants.LOG.warn(
                    "[Anima-Gift] failed to report: subject={} target_ref={} verb={}",
                    giverCredential.displayName(),
                    targetRef,
                    GIFT_VERB,
                    exception
            );
        }
    }

    private static String resolveDisplayName(LivingEntity entity) {
        String name = entity.getName().getString().trim();
        if (!name.isEmpty()) {
            return name;
        }
        return entity.getType().getDescription().getString();
    }

    private static void cleanupCooldown(long worldTime) {
        long minTick = worldTime - COOLDOWN_RETENTION_TICKS;
        for (Map.Entry<GiftKey, Long> entry : LAST_REPORT_TICKS.entrySet()) {
            if (entry.getValue() >= minTick) {
                continue;
            }
            LAST_REPORT_TICKS.remove(entry.getKey(), entry.getValue());
        }
    }

    private record GiftKey(UUID giverUuid, UUID receiverUuid, String itemId) {
    }
}
