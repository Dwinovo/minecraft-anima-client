package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.Constants;
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
import java.util.concurrent.TimeUnit;

public final class TelemetryHttpClient {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build();
    private static final Gson GSON = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String BASE_URL = "http://127.0.0.1:8000";

    private TelemetryHttpClient() {}

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

    private static String buildUrl(String endpoint) {
        String normalized = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return BASE_URL + normalized;
    }
}
