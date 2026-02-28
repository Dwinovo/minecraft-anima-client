package com.dwinovo.anima.telemetry.event.core;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.telemetry.api.AnimaApiClient;
import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventResponse;

import java.util.concurrent.CompletableFuture;

public final class EventUploadReporter {

    private final EventPoster poster;
    private final EventLogger logger;

    public EventUploadReporter() {
        this(AnimaApiClient::postEvent, new Slf4jEventLogger());
    }

    EventUploadReporter(
        EventPoster poster,
        EventLogger logger
    ) {
        this.poster = poster;
        this.logger = logger;
    }

    public void upload(
        String sessionId,
        EventRequest payload,
        String source,
        String eventName,
        String subjectKey,
        String subjectValue
    ) {
        poster.post(payload, source).thenAccept(data -> {
            if (data == null) {
                logger.warn("[{}] {} upload failed for {}={}", source, eventName, subjectKey, subjectValue);
                return;
            }

            String ackSessionId = data.session_id();
            if (ackSessionId == null || ackSessionId.isBlank()) {
                logger.warn(
                    "[{}] {} upload acknowledged without session_id for {}={}",
                    source,
                    eventName,
                    subjectKey,
                    subjectValue
                );
                return;
            }

            if (!sessionId.equals(ackSessionId)) {
                logger.warn(
                    "[{}] {} upload acknowledged with mismatched session_id={} (expected={}) for {}={}",
                    source,
                    eventName,
                    ackSessionId,
                    sessionId,
                    subjectKey,
                    subjectValue
                );
                return;
            }

            logger.info("[{}] {} created, session_id={}, {}={}", source, eventName, ackSessionId, subjectKey, subjectValue);
        });
    }

    @FunctionalInterface
    interface EventPoster {
        CompletableFuture<EventResponse.EventDataResponse> post(EventRequest payload, String source);
    }

    interface EventLogger {
        void info(String message, Object... args);

        void warn(String message, Object... args);
    }

    private static final class Slf4jEventLogger implements EventLogger {
        @Override
        public void info(String message, Object... args) {
            Constants.LOG.info(message, args);
        }

        @Override
        public void warn(String message, Object... args) {
            Constants.LOG.warn(message, args);
        }
    }
}

