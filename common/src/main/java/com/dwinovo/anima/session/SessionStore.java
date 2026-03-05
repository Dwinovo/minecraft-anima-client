package com.dwinovo.anima.session;

public final class SessionStore {

    private static volatile String sessionId = "";

    private SessionStore() {
    }

    public static String getSessionId() {
        return sessionId;
    }

    public static void setSessionId(String newSessionId) {
        if (newSessionId == null) {
            sessionId = "";
            return;
        }
        sessionId = newSessionId.trim();
    }
}
