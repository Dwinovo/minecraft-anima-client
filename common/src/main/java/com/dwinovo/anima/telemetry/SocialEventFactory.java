package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventVerb;
import com.dwinovo.anima.telemetry.model.details.KilledDetails;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.time.Instant;

public final class SocialEventFactory {

    private SocialEventFactory() {}

    public static EventRequest buildLivingDeathEvent(
        String sessionId,
        LivingEntity victim,
        DamageSource damageSource
    ) {
        Entity killer = damageSource.getEntity();
        KilledDetails details = KilledDetails.fromNative(damageSource, victim);

        return new EventRequest(
            sessionId,
            victim.level().getGameTime(),
            Instant.now().toString(),
            killer == null ? EventRequestEntityMapper.toEnvironment(victim) : EventRequestEntityMapper.toEntity(killer),
            new EventRequest.ActionRequest(SocialEventDetailsFactory.deathVerb().value(), details.toMap()),
            EventRequestEntityMapper.toEntity(victim)
        );
    }

    public static EventRequest buildPlayerChatEvent(
        String sessionId,
        ServerPlayer player,
        String rawMessage
    ) {
        return new EventRequest(
            sessionId,
            player.level().getGameTime(),
            Instant.now().toString(),
            EventRequestEntityMapper.toEntity(player),
            new EventRequest.ActionRequest(EventVerb.PLAYER_CHAT.value(), SocialEventDetailsFactory.chatDetails(rawMessage)),
            EventRequestEntityMapper.toEnvironment(player)
        );
    }
}
