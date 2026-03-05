package com.dwinovo.anima.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class EntityApiClient {

    private static final Gson GSON = new Gson();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final Pattern DOMAIN_VERB_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$");

    private final OkHttpClient httpClient;

    public EntityApiClient() {
        this(new OkHttpClient());
    }

    public EntityApiClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<SessionSummary> listSessions() throws IOException {
        String url = AnimaApiConfig.getBaseUrl() + "/api/v1/sessions";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("List sessions failed, status=" + response.code() + ", body=" + readBody(response));
            }

            JsonObject data = parseEnvelopeData(response);
            if (!data.has("items") || !data.get("items").isJsonArray()) {
                return List.of();
            }

            JsonArray items = data.getAsJsonArray("items");
            List<SessionSummary> sessions = new ArrayList<>(items.size());
            for (JsonElement itemElement : items) {
                if (!itemElement.isJsonObject()) {
                    continue;
                }
                SessionSummary summary = toSessionSummary(itemElement.getAsJsonObject());
                if (!summary.sessionId().isEmpty()) {
                    sessions.add(summary);
                }
            }
            return sessions;
        }
    }

    public SessionSummary getSession(String sessionId) throws IOException {
        String normalizedSessionId = normalizeRequiredSessionId(sessionId);
        String url = AnimaApiConfig.getBaseUrl() + "/api/v1/sessions/" + normalizedSessionId;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                throw new IOException("Session not found: " + normalizedSessionId);
            }
            if (!response.isSuccessful()) {
                throw new IOException("Get session failed, status=" + response.code() + ", body=" + readBody(response));
            }
            JsonObject data = parseEnvelopeData(response);
            return toSessionSummary(data);
        }
    }

    public RegisteredEntity registerEntity(String sessionId, String name, String source) throws IOException {
        String normalizedSessionId = normalizeRequiredSessionId(sessionId);
        String normalizedName = normalizeRequiredName(name);
        String normalizedSource = normalizeRequiredSource(source);
        String url = AnimaApiConfig.getBaseUrl() + "/api/v1/sessions/" + normalizedSessionId + "/entities";
        JsonObject payload = new JsonObject();
        payload.addProperty("name", normalizedName);
        payload.addProperty("source", normalizedSource);

        RequestBody requestBody = RequestBody.create(GSON.toJson(payload), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Register entity failed, status=" + response.code() + ", body=" + readBody(response));
            }
            JsonObject data = parseEnvelopeData(response);
            return new RegisteredEntity(
                    requiredString(data, "entity_id"),
                    requiredString(data, "access_token"),
                    optionalString(data, "display_name", normalizedName)
            );
        }
    }

    public void unregisterEntity(String sessionId, String entityId, String accessToken) throws IOException {
        String url = AnimaApiConfig.getBaseUrl() + "/api/v1/sessions/" + sessionId + "/entities/" + entityId;
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() || response.code() == 404) {
                return;
            }
            throw new IOException("Unregister entity failed, status=" + response.code() + ", body=" + readBody(response));
        }
    }

    public void reportEvent(
            String sessionId,
            String subjectEntityId,
            String accessToken,
            String verb,
            String targetRef,
            JsonObject details,
            long worldTime
    ) throws IOException {
        String url = AnimaApiConfig.getBaseUrl() + "/api/v1/sessions/" + sessionId + "/events";
        String normalizedVerb = normalizeRequiredVerb(verb);
        JsonObject payload = new JsonObject();
        payload.addProperty("world_time", Math.max(0L, worldTime));
        payload.addProperty("subject_uuid", subjectEntityId);
        payload.addProperty("verb", normalizedVerb);
        payload.addProperty("target_ref", targetRef);
        payload.add("details", details == null ? new JsonObject() : details);
        payload.addProperty("schema_version", 1);

        RequestBody requestBody = RequestBody.create(GSON.toJson(payload), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Report event failed, status=" + response.code() + ", body=" + readBody(response));
            }
            if (response.code() == 204) {
                return;
            }
            String responseBody = readBody(response);
            if (responseBody.isBlank()) {
                return;
            }
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            int code = root.has("code") ? root.get("code").getAsInt() : -1;
            if (code != 0) {
                String message = optionalString(root, "message", "unknown");
                throw new IOException("Anima event API returned code=" + code + ", message=" + message);
            }
        }
    }

    private static JsonObject parseEnvelopeData(Response response) throws IOException {
        String responseBody = readBody(response);
        if (responseBody.isEmpty()) {
            throw new IOException("Anima API returned an empty response body");
        }
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        int code = root.has("code") ? root.get("code").getAsInt() : -1;
        if (code != 0) {
            String message = optionalString(root, "message", "unknown");
            throw new IOException("Anima API returned code=" + code + ", message=" + message);
        }
        if (!root.has("data") || !root.get("data").isJsonObject()) {
            throw new IOException("Anima API response missing object field: data");
        }
        return root.getAsJsonObject("data");
    }

    private static String requiredString(JsonObject object, String field) throws IOException {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            throw new IOException("Anima API response missing field: " + field);
        }
        return object.get(field).getAsString();
    }

    private static String optionalString(JsonObject object, String field, String fallback) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return fallback;
        }
        String value = object.get(field).getAsString();
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static String readBody(Response response) throws IOException {
        if (response.body() == null) {
            return "";
        }
        return response.body().string();
    }

    private static SessionSummary toSessionSummary(JsonObject object) {
        String sessionId = optionalString(object, "session_id", "").trim();
        String name = optionalString(object, "name", sessionId);
        String description = optionalString(object, "description", "");
        return new SessionSummary(sessionId, name, description);
    }

    private static String normalizeRequiredSessionId(String sessionId) throws IOException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IOException("Session ID cannot be empty");
        }
        return sessionId.trim();
    }

    private static String normalizeRequiredName(String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IOException("Entity name cannot be empty");
        }
        return name.trim();
    }

    private static String normalizeRequiredSource(String source) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException("Entity source cannot be empty");
        }
        return source.trim();
    }

    private static String normalizeRequiredVerb(String verb) throws IOException {
        if (verb == null || verb.isBlank()) {
            throw new IOException("Event verb cannot be empty");
        }
        String normalizedVerb = verb.trim();
        if (!DOMAIN_VERB_PATTERN.matcher(normalizedVerb).matches()) {
            throw new IOException("Event verb format is invalid, expected domain.verb but got: " + normalizedVerb);
        }
        return normalizedVerb;
    }

    public record RegisteredEntity(String entityId, String accessToken, String displayName) {
    }

    public record SessionSummary(String sessionId, String name, String description) {
    }
}
