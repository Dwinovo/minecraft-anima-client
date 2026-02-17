package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.entity.IAnimaEntity;
import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventResponse;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class EntityAttackTelemetryReporter {

    private EntityAttackTelemetryReporter() {}

    public static void reportIfSupported(
        LivingEntity target,
        DamageSource damageSource,
        float damageAmount,
        String source
    ) {
        if (target.level().isClientSide() || !(target instanceof IAnimaEntity)) {
            return;
        }

        MinecraftServer server = target.level().getServer();
        if (server == null) {
            return;
        }

        String sessionId = SessionRegistrationService.getOrCreateSessionId(server);
        EventRequest payload = EntityAttackEventFactory.build(sessionId, target, damageSource, damageAmount);
        AnimaApiClient.postEvent(payload, source).thenAccept(data -> {
            if (data == null) {
                Constants.LOG.warn("[{}] Event upload failed for entity_uuid={}", source, target.getUUID());
                return;
            }

            List<EventResponse.PostResponse> newPosts = AnimaPostFeedStore.addAll(data.posts());
            if (newPosts.isEmpty()) {
                return;
            }

            server.execute(() -> broadcastPosts(server, newPosts));
        });
    }

    private static void broadcastPosts(MinecraftServer server, List<EventResponse.PostResponse> posts) {
        for (EventResponse.PostResponse post : posts) {
            String content = post.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            server.getPlayerList().broadcastSystemMessage(Component.literal("[Anima] " + content), false);
        }
    }
}
