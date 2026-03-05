package com.dwinovo.anima.agent;

public final class AnimaApiConfig {

    private static final String BASE_URL_PROPERTY = "anima.api.baseUrl";
    private static final String BASE_URL_ENV = "ANIMA_API_BASE_URL";
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8000";

    private AnimaApiConfig() {
    }

    public static String getBaseUrl() {
        String configured = System.getProperty(BASE_URL_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(BASE_URL_ENV);
        }
        if (configured == null || configured.isBlank()) {
            configured = DEFAULT_BASE_URL;
        }
        String normalized = configured.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
