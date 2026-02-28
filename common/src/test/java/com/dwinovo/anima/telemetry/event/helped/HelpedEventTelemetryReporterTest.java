package com.dwinovo.anima.telemetry.event.helped;

import com.dwinovo.anima.telemetry.api.AnimaApiClient;
import com.dwinovo.anima.telemetry.model.EventRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelpedEventTelemetryReporterTest {

    @Test
    void deduplicatesWithinCooldownWindow() {
        HelpedEventDetector detector = new HelpedEventDetector(60);
        FakeUploader uploader = new FakeUploader(List.of(successResult(), successResult()));
        HelpedEventPipeline reporter = new HelpedEventPipeline(
            detector,
            uploader,
            new HelpedEventPipeline.Config(true, 0.80D, 80L, 99, 60),
            NOOP_LOGGER
        );

        HelpedEventDetector.EntitySnapshot helper = entity("helper", 0.0, 0.0, 20.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot beneficiary = entity("beneficiary", 2.0, 0.0, 12.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot threat = entity("threat", 1.0, 0.0, 20.0F, 20.0F);

        reporter.onAttack(threat, beneficiary, 6.0F, 100L, "s1", "test");
        reporter.onAttack(helper, threat, 12.0F, 102L, "s1", "test");

        reporter.onAttack(threat, beneficiary, 6.0F, 120L, "s1", "test");
        reporter.onAttack(helper, threat, 12.0F, 121L, "s1", "test");

        assertEquals(1, uploader.payloads.size());
    }

    @Test
    void opensCircuitAfterUnsupportedVerb422AndSkipsFurtherUploads() {
        HelpedEventDetector detector = new HelpedEventDetector(60);
        FakeUploader uploader = new FakeUploader(List.of(
            unsupportedVerbResult(),
            successResult()
        ));
        HelpedEventPipeline reporter = new HelpedEventPipeline(
            detector,
            uploader,
            new HelpedEventPipeline.Config(true, 0.80D, 10L, 99, 60),
            NOOP_LOGGER
        );

        HelpedEventDetector.EntitySnapshot helper = entity("helper", 0.0, 0.0, 20.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot beneficiary = entity("beneficiary", 2.0, 0.0, 12.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot threat = entity("threat", 1.0, 0.0, 20.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot beneficiary2 = entity("beneficiary2", 3.0, 0.0, 16.0F, 20.0F);
        HelpedEventDetector.EntitySnapshot threat2 = entity("threat2", 4.0, 0.0, 20.0F, 20.0F);

        reporter.onAttack(threat, beneficiary, 6.0F, 100L, "s1", "test");
        reporter.onAttack(helper, threat, 12.0F, 102L, "s1", "test");

        reporter.onAttack(threat2, beneficiary2, 6.0F, 120L, "s1", "test");
        reporter.onAttack(helper, threat2, 12.0F, 121L, "s1", "test");

        assertEquals(1, uploader.payloads.size());
    }

    private static AnimaApiClient.EventPostResult successResult() {
        return new AnimaApiClient.EventPostResult(true, 201, 0, "ok", new com.dwinovo.anima.telemetry.model.EventResponse.EventDataResponse("s1"), "{}");
    }

    private static AnimaApiClient.EventPostResult unsupportedVerbResult() {
        return new AnimaApiClient.EventPostResult(false, 422, 422, "unsupported verb HELPED", null, "unsupported verb");
    }

    private static HelpedEventDetector.EntitySnapshot entity(
        String id,
        double x,
        double z,
        float health,
        float maxHealth
    ) {
        return new HelpedEventDetector.EntitySnapshot(
            id,
            "minecraft:zombie",
            id,
            new HelpedEventDetector.LocationSnapshot("minecraft:overworld", "minecraft:plains", x, 64.0D, z),
            health,
            maxHealth
        );
    }

    private static final class FakeUploader implements HelpedEventPipeline.EventUploader {
        private final List<AnimaApiClient.EventPostResult> scriptedResults;
        private final List<EventRequest> payloads = new ArrayList<>();
        private int index;

        private FakeUploader(List<AnimaApiClient.EventPostResult> scriptedResults) {
            this.scriptedResults = scriptedResults;
        }

        @Override
        public CompletableFuture<AnimaApiClient.EventPostResult> upload(EventRequest payload, String source) {
            payloads.add(payload);
            AnimaApiClient.EventPostResult result = scriptedResults.get(Math.min(index, scriptedResults.size() - 1));
            index++;
            return CompletableFuture.completedFuture(result);
        }
    }

    private static final HelpedEventPipeline.EventLogger NOOP_LOGGER = new HelpedEventPipeline.EventLogger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}
    };
}

