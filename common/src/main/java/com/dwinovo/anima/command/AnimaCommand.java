package com.dwinovo.anima.command;

import com.dwinovo.anima.telemetry.AnimaApiClient;
import com.dwinovo.anima.telemetry.SessionRegistrationService;
import com.dwinovo.anima.telemetry.model.TickResponse;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class AnimaCommand {

    private static final String COMMAND_SOURCE = "anima-command-tick";

    private AnimaCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("anima")
                .requires(source -> source.hasPermission(2))
                .executes(context -> showUsage(context.getSource()))
                .then(
                    Commands.literal("tick")
                        .executes(context -> executeTick(context.getSource()))
                )
        );
    }

    private static int showUsage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Usage: /anima tick"), false);
        return 1;
    }

    private static int executeTick(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        String sessionId = SessionRegistrationService.getOrCreateSessionId(server);

        source.sendSuccess(() -> Component.literal("Anima tick started, session_id=" + sessionId), false);
        AnimaApiClient.postEventTick(sessionId, COMMAND_SOURCE).thenAccept(data ->
            server.execute(() -> reportTickResult(source, sessionId, data))
        );
        return 1;
    }

    private static void reportTickResult(
        CommandSourceStack source,
        String sessionId,
        TickResponse.TickDataResponse data
    ) {
        if (data == null) {
            source.sendFailure(Component.literal("Anima tick failed for session_id=" + sessionId));
            return;
        }

        String ackSessionId = data.session_id();
        if (ackSessionId == null || ackSessionId.isBlank()) {
            source.sendFailure(Component.literal("Anima tick returned empty session_id"));
            return;
        }

        if (!sessionId.equals(ackSessionId)) {
            source.sendFailure(
                Component.literal("Anima tick session_id mismatch: expected=" + sessionId + ", actual=" + ackSessionId)
            );
            return;
        }

        source.sendSuccess(
            () -> Component.literal(
                "Anima tick finished, session_id="
                    + ackSessionId
                    + ", total_agents="
                    + data.total_agents()
                    + ", succeeded="
                    + data.succeeded()
                    + ", failed="
                    + data.failed()
            ),
            false
        );

        for (TickResponse.TickAgentResultResponse result : data.results()) {
            String agentUuid = sanitize(result.agent_uuid(), "unknown-agent");
            String message = sanitize(result.message(), "");
            String status = result.success() ? "SUCCESS" : "FAILED";
            String line = "[" + status + "] " + agentUuid + (message.isBlank() ? "" : ": " + message);
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
