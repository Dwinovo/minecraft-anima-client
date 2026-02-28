package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.telemetry.model.EventRequest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class HelpedEventPipeline {

    static final long DEFAULT_WINDOW_TICKS = 60L;
    static final long DEFAULT_DEDUP_COOLDOWN_TICKS = 80L;
    static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.80D;
    static final int DEFAULT_MAX_REPORTS_PER_WINDOW = 12;
    static final long DEFAULT_RATE_LIMIT_WINDOW_TICKS = 60L;
    private static final long METRICS_LOG_INTERVAL_TICKS = 200L;
    private static final String ENABLE_HELP_EVENT = "ENABLE_HELP_EVENT";
    private static final String HELPED_SOURCE_SUFFIX = "helped";

    private final HelpedEventDetector detector;
    private final EventUploader uploader;
    private final Config config;
    private final EventLogger logger;
    private final Map<String, Long> dedupCache = new HashMap<>();
    private final Deque<Long> sentTicks = new ArrayDeque<>();

    private long candidateCount;
    private long thresholdPassedCount;
    private long reportedCount;
    private long dedupedCount;
    private long rateLimitedCount;
    private double confidenceTotal;
    private long lastMetricsLogTick = Long.MIN_VALUE;

    private volatile boolean helpedCircuitOpen;

    HelpedEventPipeline(
        HelpedEventDetector detector,
        EventUploader uploader,
        Config config
    ) {
        this(detector, uploader, config, new Slf4jEventLogger());
    }

    HelpedEventPipeline(
        HelpedEventDetector detector,
        EventUploader uploader,
        Config config,
        EventLogger logger
    ) {
        this.detector = detector;
        this.uploader = uploader;
        this.config = config;
        this.logger = logger;
    }

    void onAttack(
        HelpedEventDetector.EntitySnapshot helper,
        HelpedEventDetector.EntitySnapshot threat,
        float damage,
        long worldTick,
        String sessionId,
        String source
    ) {
        if (!config.enabled() || helper == null || threat == null) {
            return;
        }

        List<HelpedEventDetector.HelpedCandidate> candidates = detector.onAttack(helper, threat, damage, worldTick);
        processCandidates(candidates, worldTick, sessionId, source);
    }

    void onDeath(
        HelpedEventDetector.EntitySnapshot killer,
        HelpedEventDetector.EntitySnapshot victim,
        String reason,
        long worldTick,
        String sessionId,
        String source
    ) {
        if (!config.enabled() || victim == null) {
            return;
        }

        List<HelpedEventDetector.HelpedCandidate> candidates = detector.onDeath(killer, victim, reason, worldTick);
        processCandidates(candidates, worldTick, sessionId, source);
    }

    private void processCandidates(
        List<HelpedEventDetector.HelpedCandidate> candidates,
        long worldTick,
        String sessionId,
        String source
    ) {
        if (candidates.isEmpty()) {
            return;
        }

        int passed = 0;
        int deduped = 0;
        int reported = 0;
        int rateLimited = 0;
        double batchConfidence = 0.0D;

        for (HelpedEventDetector.HelpedCandidate candidate : candidates) {
            batchConfidence += candidate.confidence();

            if (candidate.confidence() < config.confidenceThreshold()) {
                continue;
            }
            passed++;

            if (helpedCircuitOpen) {
                logCircuitDrop(source, candidate);
                continue;
            }

            if (isDeduped(candidate, worldTick)) {
                deduped++;
                continue;
            }

            if (isRateLimited(worldTick)) {
                rateLimited++;
                continue;
            }

            markSent(candidate, worldTick);
            reported++;

            EventRequest payload = HelpedEventFactory.build(sessionId, worldTick, candidate);
            uploader.upload(payload, helpedSource(source)).thenAccept(result -> onHelpedUploadResult(result, source));
        }

        recordMetrics(candidates.size(), passed, reported, deduped, rateLimited, batchConfidence, worldTick, source);
    }

    private synchronized void recordMetrics(
        int candidates,
        int passed,
        int reported,
        int deduped,
        int rateLimited,
        double batchConfidence,
        long worldTick,
        String source
    ) {
        candidateCount += candidates;
        thresholdPassedCount += passed;
        reportedCount += reported;
        dedupedCount += deduped;
        rateLimitedCount += rateLimited;
        confidenceTotal += batchConfidence;

        if (lastMetricsLogTick == Long.MIN_VALUE || worldTick - lastMetricsLogTick >= METRICS_LOG_INTERVAL_TICKS) {
            double averageConfidence = candidateCount == 0 ? 0.0D : confidenceTotal / (double) candidateCount;
            logger.info(
                "[{}] HELPED metrics candidates={}, passed={}, reported={}, deduped={}, avg_confidence={}, rate_limited={}",
                source,
                candidateCount,
                thresholdPassedCount,
                reportedCount,
                dedupedCount,
                String.format(Locale.ROOT, "%.4f", averageConfidence),
                rateLimitedCount
            );
            lastMetricsLogTick = worldTick;
        }
    }

    private synchronized boolean isDeduped(HelpedEventDetector.HelpedCandidate candidate, long worldTick) {
        clearExpiredDedup(worldTick);

        String key = dedupKey(candidate);
        Long lastTick = dedupCache.get(key);
        return lastTick != null && worldTick - lastTick < config.dedupCooldownTicks();
    }

    private synchronized void markSent(HelpedEventDetector.HelpedCandidate candidate, long worldTick) {
        clearExpiredDedup(worldTick);
        dedupCache.put(dedupKey(candidate), worldTick);
        sentTicks.addLast(worldTick);
    }

    private synchronized boolean isRateLimited(long worldTick) {
        long minTick = worldTick - config.rateLimitWindowTicks();
        while (!sentTicks.isEmpty()) {
            Long oldest = sentTicks.peekFirst();
            if (oldest == null || oldest >= minTick) {
                break;
            }
            sentTicks.removeFirst();
        }
        return sentTicks.size() >= config.maxReportsPerWindow();
    }

    private void onHelpedUploadResult(AnimaApiClient.EventPostResult result, String source) {
        if (result == null) {
            return;
        }

        if (result.success()) {
            return;
        }

        if (result.isUnsupportedVerb422()) {
            boolean wasOpen = helpedCircuitOpen;
            helpedCircuitOpen = true;
            if (!wasOpen) {
                logger.warn(
                    "[{}] HELPED upload received unsupported verb/422, circuit opened. ATTACKED/KILLED uploads remain active.",
                    source
                );
            }
        }
    }

    private synchronized void clearExpiredDedup(long worldTick) {
        Iterator<Map.Entry<String, Long>> iterator = dedupCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (worldTick - entry.getValue() >= config.dedupCooldownTicks()) {
                iterator.remove();
            }
        }
    }

    private static String dedupKey(HelpedEventDetector.HelpedCandidate candidate) {
        return candidate.helperId() + "|" + candidate.beneficiaryId() + "|" + candidate.threatId();
    }

    private void logCircuitDrop(String source, HelpedEventDetector.HelpedCandidate candidate) {
        logger.info(
            "[{}] HELPED circuit-open fallback helper={} beneficiary={} threat={} confidence={}",
            source,
            candidate.helperId(),
            candidate.beneficiaryId(),
            candidate.threatId(),
            String.format(Locale.ROOT, "%.4f", candidate.confidence())
        );
    }

    private static String helpedSource(String source) {
        return source + "-" + HELPED_SOURCE_SUFFIX;
    }

    private static boolean isHelpEventEnabled() {
        String property = System.getProperty(ENABLE_HELP_EVENT);
        if (property != null) {
            return parseBoolean(property, true);
        }

        String env = System.getenv(ENABLE_HELP_EVENT);
        if (env != null) {
            return parseBoolean(env, true);
        }

        return true;
    }

    private static boolean parseBoolean(String rawValue, boolean defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> defaultValue;
        };
    }

    @FunctionalInterface
    interface EventUploader {
        CompletableFuture<AnimaApiClient.EventPostResult> upload(EventRequest payload, String source);
    }

    interface EventLogger {
        void info(String message, Object... args);

        void warn(String message, Object... args);
    }

    record Config(
        boolean enabled,
        double confidenceThreshold,
        long dedupCooldownTicks,
        int maxReportsPerWindow,
        long rateLimitWindowTicks
    ) {
        static Config fromEnvironment() {
            return new Config(
                isHelpEventEnabled(),
                DEFAULT_CONFIDENCE_THRESHOLD,
                DEFAULT_DEDUP_COOLDOWN_TICKS,
                DEFAULT_MAX_REPORTS_PER_WINDOW,
                DEFAULT_RATE_LIMIT_WINDOW_TICKS
            );
        }
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
