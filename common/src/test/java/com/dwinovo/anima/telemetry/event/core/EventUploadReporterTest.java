package com.dwinovo.anima.telemetry.event.core;

import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventUploadReporterTest {

    @Test
    void logsInfoWhenSessionMatches() {
        CapturingLogger logger = new CapturingLogger();
        EventUploadReporter reporter = new EventUploadReporter(
            (payload, source) -> CompletableFuture.completedFuture(new EventResponse.EventDataResponse("s1")),
            logger
        );

        reporter.upload("s1", payload("s1"), "test", "Event", "entity_uuid", "entity-1");

        assertEquals(1, logger.infoCount);
        assertEquals(0, logger.warnCount);
    }

    @Test
    void logsWarnWhenSessionMismatched() {
        CapturingLogger logger = new CapturingLogger();
        EventUploadReporter reporter = new EventUploadReporter(
            (payload, source) -> CompletableFuture.completedFuture(new EventResponse.EventDataResponse("other")),
            logger
        );

        reporter.upload("s1", payload("s1"), "test", "Event", "entity_uuid", "entity-1");

        assertEquals(0, logger.infoCount);
        assertEquals(1, logger.warnCount);
    }

    private static EventRequest payload(String sessionId) {
        return new EventRequest(
            sessionId,
            1L,
            "2026-01-01T00:00:00Z",
            null,
            new EventRequest.ActionRequest("ATTACKED", Map.of()),
            null
        );
    }

    private static final class CapturingLogger implements EventUploadReporter.EventLogger {
        private int infoCount;
        private int warnCount;

        @Override
        public void info(String message, Object... args) {
            infoCount++;
        }

        @Override
        public void warn(String message, Object... args) {
            warnCount++;
        }
    }
}

