package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventResponse;
import com.dwinovo.anima.telemetry.model.TickResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class AnimaApiClient {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build();
    private static final Gson GSON = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String BASE_URL = "http://localhost:8000";
    private static final String AGENTS_ENDPOINT = "/api/agents";
    private static final String SESSIONS_ENDPOINT = "/api/sessions";
    private static final String EVENTS_ENDPOINT = "/api/events";
    private static final String EVENT_TICK_ENDPOINT = "/api/events/tick";

    private AnimaApiClient() {}

    public static void post(String endpoint, Object requestBody, String source) {
        postJson(endpoint, GSON.toJson(requestBody), source);
    }

    public static void postJson(String endpoint, String requestBodyJson, String source) {
        String url = buildUrl(endpoint);
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(requestBodyJson, JSON))
            .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                Constants.LOG.warn("[{}] POST {} failed: {}", source, url, exception.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    String content = body == null ? "" : body.string();
                    Constants.LOG.info("[{}] POST {} -> HTTP {}, response: {}", source, url, response.code(), content);
                }
            }
        });
    }

    public static CompletableFuture<Boolean> postAgentInit(
        String sessionId,
        String entityUuid,
        String entityType,
        String profile,
        String source
    ) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        String url = buildUrl(AGENTS_ENDPOINT);

        AgentInitRequest payload = new AgentInitRequest(sessionId, entityUuid, entityType, profile);
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(GSON.toJson(payload), JSON))
            .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                Constants.LOG.warn("[{}] POST {} failed: {}", source, url, exception.getMessage());
                result.complete(false);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    String content = body == null ? "" : body.string();
                    if (response.isSuccessful()) {
                        Constants.LOG.info("[{}] POST {} -> HTTP {}, response: {}", source, url, response.code(), content);
                        result.complete(true);
                    } else {
                        Constants.LOG.warn("[{}] POST {} -> HTTP {}, response: {}", source, url, response.code(), content);
                        result.complete(false);
                    }
                }
            }
        });

        return result;
    }

    public static CompletableFuture<Boolean> postSessionInit(String sessionId, String worldName, String source) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        String url = buildUrl(SESSIONS_ENDPOINT);

        SessionInitRequest payload = new SessionInitRequest(sessionId, worldName);
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(GSON.toJson(payload), JSON))
            .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                Constants.LOG.warn("[{}] POST {} failed: {}", source, url, exception.getMessage());
                result.complete(false);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    String content = body == null ? "" : body.string();
                    if (response.isSuccessful()) {
                        Constants.LOG.info("[{}] POST {} -> HTTP {}, response: {}", source, url, response.code(), content);
                        result.complete(true);
                    } else {
                        Constants.LOG.warn("[{}] POST {} -> HTTP {}, response: {}", source, url, response.code(), content);
                        result.complete(false);
                    }
                }
            }
        });

        return result;
    }

    public static CompletableFuture<EventResponse.EventDataResponse> postEvent(EventRequest eventPayload, String source) {
        CompletableFuture<EventResponse.EventDataResponse> result = new CompletableFuture<>();
        String url = buildUrl(EVENTS_ENDPOINT);

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(GSON.toJson(eventPayload), JSON))
            .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                Constants.LOG.warn("[{}] POST {} failed: {}", source, url, exception.getMessage());
                result.complete(null);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    String content = body == null ? "" : body.string();
                    try {
                        EventResponse parsed = GSON.fromJson(content, EventResponse.class);
                        int appCode = parsed == null ? -1 : parsed.code();
                        String appMessage = parsed == null ? null : parsed.message();
                        EventResponse.EventDataResponse data = parsed == null ? null : parsed.data();
                        String ackSessionId = data == null ? null : data.session_id();

                        boolean success = response.code() == 201
                            && appCode == 0
                            && ackSessionId != null
                            && !ackSessionId.isBlank();

                        if (success) {
                            Constants.LOG.info(
                                "[{}] POST {} -> HTTP {}, code={}, session_id={}, response: {}",
                                source,
                                url,
                                response.code(),
                                appCode,
                                ackSessionId,
                                content
                            );
                            result.complete(data);
                        } else {
                            Constants.LOG.warn(
                                "[{}] POST {} -> HTTP {}, code={}, message={}, response: {}",
                                source,
                                url,
                                response.code(),
                                appCode,
                                appMessage,
                                content
                            );
                            result.complete(null);
                        }
                    } catch (Exception exception) {
                        Constants.LOG.warn("[{}] POST {} parse response failed: {}", source, url, exception.getMessage());
                        result.complete(null);
                    }
                }
            }
        });

        return result;
    }

    public static CompletableFuture<TickResponse.TickDataResponse> postEventTick(String sessionId, String source) {
        CompletableFuture<TickResponse.TickDataResponse> result = new CompletableFuture<>();
        String url = buildUrl(EVENT_TICK_ENDPOINT);

        EventTickRequest payload = new EventTickRequest(sessionId);
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(GSON.toJson(payload), JSON))
            .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                Constants.LOG.warn("[{}] POST {} failed: {}", source, url, exception.getMessage());
                result.complete(null);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    String content = body == null ? "" : body.string();
                    try {
                        TickResponse.TickDataResponse data = parseTickData(response.code(), content, sessionId);
                        if (data != null) {
                            Constants.LOG.info(
                                "[{}] POST {} -> HTTP {}, code={}, session_id={}, total={}, succeeded={}, failed={}, response: {}",
                                source,
                                url,
                                response.code(),
                                0,
                                data.session_id(),
                                data.total_agents(),
                                data.succeeded(),
                                data.failed(),
                                content
                            );
                            result.complete(data);
                        } else {
                            Constants.LOG.warn(
                                "[{}] POST {} -> HTTP {}, unresolved tick response: {}",
                                source,
                                url,
                                response.code(),
                                content
                            );
                            result.complete(null);
                        }
                    } catch (Exception exception) {
                        Constants.LOG.warn("[{}] POST {} parse response failed: {}", source, url, exception.getMessage());
                        result.complete(null);
                    }
                }
            }
        });

        return result;
    }

    static TickResponse.TickDataResponse parseTickData(
        int httpCode,
        String content,
        String requestedSessionId
    ) {
        if (!isSuccessHttpStatus(httpCode) || content == null || content.isBlank()) {
            return null;
        }

        TickResponse.TickDataResponse legacyData = parseLegacyTickData(content);
        if (legacyData != null) {
            return legacyData;
        }

        TickResponse.TickDataResponse wrappedAcceptedData = parseWrappedAcceptedData(content, requestedSessionId);
        if (wrappedAcceptedData != null) {
            return wrappedAcceptedData;
        }

        return parseBareAcceptedData(content, requestedSessionId);
    }

    private static TickResponse.TickDataResponse parseLegacyTickData(String content) {
        TickResponse parsed = GSON.fromJson(content, TickResponse.class);
        if (parsed == null || parsed.code() != 0 || parsed.data() == null) {
            return null;
        }

        String ackSessionId = parsed.data().session_id();
        if (ackSessionId == null || ackSessionId.isBlank()) {
            return null;
        }
        return parsed.data();
    }

    private static TickResponse.TickDataResponse parseWrappedAcceptedData(
        String content,
        String requestedSessionId
    ) {
        TickAcceptedResponse parsed = GSON.fromJson(content, TickAcceptedResponse.class);
        TickAcceptedData acceptedData = parsed == null ? null : parsed.data();
        String status = acceptedData == null ? null : acceptedData.status();
        if (parsed == null || parsed.code() != 0 || !isAcceptedStatus(status)) {
            return null;
        }

        String ackSessionId = firstNonBlank(acceptedData.session_id(), requestedSessionId);
        if (ackSessionId == null) {
            return null;
        }
        return new TickResponse.TickDataResponse(ackSessionId, 0, 0, 0, null);
    }

    private static TickResponse.TickDataResponse parseBareAcceptedData(
        String content,
        String requestedSessionId
    ) {
        JsonElement root = JsonParser.parseString(content);
        if (!root.isJsonObject()) {
            return null;
        }

        JsonObject object = root.getAsJsonObject();
        String status = readString(object, "status");
        if (!isAcceptedStatus(status)) {
            return null;
        }

        String ackSessionId = firstNonBlank(readString(object, "session_id"), requestedSessionId);
        if (ackSessionId == null) {
            return null;
        }
        return new TickResponse.TickDataResponse(ackSessionId, 0, 0, 0, null);
    }

    private static boolean isSuccessHttpStatus(int httpCode) {
        return httpCode >= 200 && httpCode < 300;
    }

    private static boolean isAcceptedStatus(String status) {
        return status != null && "accepted".equalsIgnoreCase(status);
    }

    private static String readString(JsonObject object, String key) {
        if (!object.has(key)) {
            return null;
        }

        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        return value.getAsString();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static String buildUrl(String endpoint) {
        String normalized = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return BASE_URL + normalized;
    }

    private record AgentInitRequest(
        String session_id,
        String entity_uuid,
        String entity_type,
        String profile
    ) {}

    private record SessionInitRequest(
        String session_id,
        String world_name
    ) {}

    private record EventTickRequest(
        String session_id
    ) {}

    private record TickAcceptedResponse(
        int code,
        String message,
        TickAcceptedData data
    ) {}

    private record TickAcceptedData(
        String status,
        String session_id
    ) {}
}
