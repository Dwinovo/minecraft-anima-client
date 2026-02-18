package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.telemetry.model.EventRequest;
import com.dwinovo.anima.telemetry.model.EventResponse;
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
}
