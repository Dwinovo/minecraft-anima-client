package com.dwinovo.anima.ui;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.agent.EntityApiClient;
import com.dwinovo.anima.agent.EntityLifecycle;
import com.dwinovo.anima.session.SessionStore;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class SessionJoinFragment extends Fragment {

    private static final String TITLE_TEXT = "Select Session";
    private static final String DESCRIPTION_TEXT = "Choose a session from the server list, validate it, then connect.";
    private static final String SESSIONS_HEADER_TEXT = "Available Sessions";
    private static final String REFRESH_TEXT = "Refresh";
    private static final String CONNECT_TEXT = "Connect";
    private static final String CLEAR_TEXT = "Clear";
    private static final String HOTKEY_HINT_TEXT = "Hotkey: I";

    private static final String LOADING_TEXT = "Loading sessions...";
    private static final String NO_SESSION_TEXT = "No session found on server";
    private static final String SELECT_SESSION_TEXT = "Select a session first";
    private static final String VALIDATING_TEXT = "Validating selected session...";

    private static final int COLOR_SCRIM = 0xB3000000;
    private static final int COLOR_SURFACE_CARD = 0xFFF4F5F7;
    private static final int COLOR_SURFACE_LIST = 0xFFFFFFFF;
    private static final int COLOR_PRIMARY = 0xFF2F6DF6;
    private static final int COLOR_PRIMARY_TEXT = 0xFFFFFFFF;
    private static final int COLOR_SECONDARY = 0xFFE5E7EB;
    private static final int COLOR_TEXT_PRIMARY = 0xFF111827;
    private static final int COLOR_TEXT_SECONDARY = 0xFF6B7280;
    private static final int COLOR_TEXT_SUCCESS = 0xFF0F9D58;
    private static final int COLOR_TEXT_ERROR = 0xFFD93025;

    private static final EntityApiClient API_CLIENT = new EntityApiClient();
    private static final ExecutorService SESSION_QUERY_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anima-session-query");
        thread.setDaemon(true);
        return thread;
    });

    private final List<EntityApiClient.SessionSummary> availableSessions = new ArrayList<>();
    private String selectedSessionId = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var context = requireContext();

        var root = new FrameLayout(context);
        root.setBackground(new ColorDrawable(COLOR_SCRIM));

        var panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int panelPadding = panel.dp(16);
        panel.setPadding(panelPadding, panelPadding, panelPadding, panelPadding);
        panel.setBackground(roundedRect(panel, COLOR_SURFACE_CARD, 12));

        var title = new TextView(context);
        title.setText(TITLE_TEXT);
        title.setTextSize(22);
        title.setTextStyle(Typeface.BOLD);
        title.setTextColor(COLOR_TEXT_PRIMARY);
        panel.addView(title, withBottomMargin(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT), panel.dp(6)));

        var description = new TextView(context);
        description.setText(DESCRIPTION_TEXT);
        description.setTextSize(12);
        description.setTextColor(COLOR_TEXT_SECONDARY);
        panel.addView(description, withBottomMargin(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT), panel.dp(10)));

        var selectedSessionView = new TextView(context);
        selectedSessionView.setTextSize(12);
        setStatus(selectedSessionView, selectedSessionText(), COLOR_TEXT_SECONDARY);
        panel.addView(selectedSessionView, withBottomMargin(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT), panel.dp(6)));

        var currentSessionView = new TextView(context);
        currentSessionView.setTextSize(12);
        setStatus(currentSessionView, currentSessionText(), COLOR_TEXT_SECONDARY);
        panel.addView(currentSessionView, withBottomMargin(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT), panel.dp(12)));

        var sessionsHeader = new TextView(context);
        sessionsHeader.setText(SESSIONS_HEADER_TEXT);
        sessionsHeader.setTextSize(11);
        sessionsHeader.setTextStyle(Typeface.BOLD);
        sessionsHeader.setTextColor(COLOR_TEXT_SECONDARY);
        panel.addView(sessionsHeader, withBottomMargin(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT), panel.dp(4)));

        var sessionList = new LinearLayout(context);
        sessionList.setOrientation(LinearLayout.VERTICAL);
        int listPadding = sessionList.dp(6);
        sessionList.setPadding(listPadding, listPadding, listPadding, listPadding);
        sessionList.setBackground(roundedRect(sessionList, COLOR_SURFACE_LIST, 8));
        panel.addView(sessionList, withBottomMargin(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT), panel.dp(10)));

        var status = new TextView(context);
        status.setTextSize(12);
        setStatus(status, LOADING_TEXT, COLOR_TEXT_SECONDARY);
        panel.addView(status, withBottomMargin(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT), panel.dp(8)));

        var actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        var refreshButton = new Button(context);
        refreshButton.setText(REFRESH_TEXT);
        refreshButton.setTextColor(COLOR_TEXT_PRIMARY);
        refreshButton.setBackground(roundedRect(refreshButton, COLOR_SECONDARY, 10));

        var connectButton = new Button(context);
        connectButton.setText(CONNECT_TEXT);
        connectButton.setTextStyle(Typeface.BOLD);
        connectButton.setTextColor(COLOR_PRIMARY_TEXT);
        connectButton.setBackground(roundedRect(connectButton, COLOR_PRIMARY, 10));

        var clearButton = new Button(context);
        clearButton.setText(CLEAR_TEXT);
        clearButton.setTextColor(COLOR_TEXT_PRIMARY);
        clearButton.setBackground(roundedRect(clearButton, COLOR_SECONDARY, 10));

        int buttonPadding = refreshButton.dp(10);
        refreshButton.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);
        connectButton.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);
        clearButton.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);

        refreshButton.setOnClickListener(v -> loadSessions(root, sessionList, selectedSessionView, status));
        connectButton.setOnClickListener(v -> connectSelectedSession(root, status, currentSessionView));
        clearButton.setOnClickListener(v -> {
            SessionStore.setSessionId("");
            EntityLifecycle.onSessionChanged("");
            setStatus(currentSessionView, currentSessionText(), COLOR_TEXT_SECONDARY);
            setStatus(status, "Cleared current session", COLOR_TEXT_SECONDARY);
        });

        var refreshParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        refreshParams.rightMargin = actionRow.dp(6);
        actionRow.addView(refreshButton, refreshParams);

        var connectParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        connectParams.rightMargin = actionRow.dp(6);
        actionRow.addView(connectButton, connectParams);

        actionRow.addView(clearButton, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f));
        panel.addView(actionRow, withBottomMargin(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT), panel.dp(8)));

        var hint = new TextView(context);
        hint.setText(HOTKEY_HINT_TEXT);
        hint.setTextSize(11);
        hint.setTextColor(COLOR_TEXT_SECONDARY);
        panel.addView(hint, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        var panelParams = new FrameLayout.LayoutParams(panel.dp(520), WRAP_CONTENT);
        panelParams.gravity = Gravity.CENTER;
        root.addView(panel, panelParams);

        selectedSessionId = SessionStore.getSessionId();
        loadSessions(root, sessionList, selectedSessionView, status);

        return root;
    }

    private void loadSessions(View root, LinearLayout sessionList, TextView selectedSessionView, TextView statusView) {
        setStatus(statusView, LOADING_TEXT, COLOR_TEXT_SECONDARY);
        SESSION_QUERY_EXECUTOR.execute(() -> {
            try {
                List<EntityApiClient.SessionSummary> loadedSessions = API_CLIENT.listSessions();
                root.post(() -> {
                    availableSessions.clear();
                    availableSessions.addAll(loadedSessions);

                    String currentSessionId = SessionStore.getSessionId();
                    if (!currentSessionId.isEmpty() && containsSessionId(currentSessionId)) {
                        selectedSessionId = currentSessionId;
                    } else if (selectedSessionId.isEmpty() || !containsSessionId(selectedSessionId)) {
                        selectedSessionId = availableSessions.isEmpty() ? "" : availableSessions.get(0).sessionId();
                    }

                    renderSessionList(sessionList, selectedSessionView);
                    if (availableSessions.isEmpty()) {
                        setStatus(statusView, NO_SESSION_TEXT, COLOR_TEXT_SECONDARY);
                    } else {
                        setStatus(statusView, "Loaded " + availableSessions.size() + " sessions", COLOR_TEXT_SUCCESS);
                    }
                });
            } catch (Exception exception) {
                Constants.LOG.warn("Failed to load session list", exception);
                root.post(() -> setStatus(
                        statusView,
                        "Failed to load sessions: " + compactErrorMessage(exception),
                        COLOR_TEXT_ERROR
                ));
            }
        });
    }

    private void connectSelectedSession(View root, TextView statusView, TextView currentSessionView) {
        String candidateSessionId = selectedSessionId == null ? "" : selectedSessionId.trim();
        if (candidateSessionId.isEmpty()) {
            setStatus(statusView, SELECT_SESSION_TEXT, COLOR_TEXT_ERROR);
            return;
        }

        setStatus(statusView, VALIDATING_TEXT, COLOR_TEXT_SECONDARY);
        SESSION_QUERY_EXECUTOR.execute(() -> {
            try {
                EntityApiClient.SessionSummary summary = API_CLIENT.getSession(candidateSessionId);
                root.post(() -> {
                    SessionStore.setSessionId(summary.sessionId());
                    EntityLifecycle.onSessionChanged(summary.sessionId());
                    Constants.LOG.info("Selected session '{}' ({})", summary.name(), summary.sessionId());
                    setStatus(currentSessionView, currentSessionText(), COLOR_TEXT_SECONDARY);
                    setStatus(statusView, "Connected to " + summary.name(), COLOR_TEXT_SUCCESS);
                });
            } catch (Exception exception) {
                Constants.LOG.warn("Failed to validate selected session {}", candidateSessionId, exception);
                root.post(() -> setStatus(
                        statusView,
                        "Session validation failed: " + compactErrorMessage(exception),
                        COLOR_TEXT_ERROR
                ));
            }
        });
    }

    private void renderSessionList(LinearLayout sessionList, TextView selectedSessionView) {
        sessionList.removeAllViews();
        if (availableSessions.isEmpty()) {
            var emptyText = new TextView(requireContext());
            emptyText.setText(NO_SESSION_TEXT);
            emptyText.setTextSize(12);
            emptyText.setTextColor(COLOR_TEXT_SECONDARY);
            sessionList.addView(emptyText, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            setStatus(selectedSessionView, selectedSessionText(), COLOR_TEXT_SECONDARY);
            return;
        }

        for (int i = 0; i < availableSessions.size(); i++) {
            EntityApiClient.SessionSummary session = availableSessions.get(i);
            var sessionButton = new Button(requireContext());
            boolean selected = session.sessionId().equals(selectedSessionId);

            sessionButton.setText(buildSessionButtonText(session));
            sessionButton.setTextColor(selected ? COLOR_PRIMARY_TEXT : COLOR_TEXT_PRIMARY);
            sessionButton.setBackground(roundedRect(sessionButton, selected ? COLOR_PRIMARY : COLOR_SECONDARY, 8));
            sessionButton.setTextSize(12);

            int buttonPadding = sessionButton.dp(8);
            sessionButton.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);
            sessionButton.setOnClickListener(v -> {
                selectedSessionId = session.sessionId();
                renderSessionList(sessionList, selectedSessionView);
            });

            var itemParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            if (i < availableSessions.size() - 1) {
                itemParams.bottomMargin = sessionButton.dp(6);
            }
            sessionList.addView(sessionButton, itemParams);
        }

        setStatus(selectedSessionView, selectedSessionText(), COLOR_TEXT_SECONDARY);
    }

    private boolean containsSessionId(String sessionId) {
        for (EntityApiClient.SessionSummary summary : availableSessions) {
            if (summary.sessionId().equals(sessionId)) {
                return true;
            }
        }
        return false;
    }

    private String selectedSessionText() {
        if (selectedSessionId == null || selectedSessionId.isEmpty()) {
            return "Selected Session: (none)";
        }
        return "Selected Session: " + resolveSessionName(selectedSessionId) + " (" + selectedSessionId + ")";
    }

    private String resolveSessionName(String sessionId) {
        for (EntityApiClient.SessionSummary summary : availableSessions) {
            if (summary.sessionId().equals(sessionId)) {
                return summary.name();
            }
        }
        return sessionId;
    }

    private static String buildSessionButtonText(EntityApiClient.SessionSummary session) {
        String shortId = session.sessionId();
        if (shortId.length() > 12) {
            shortId = shortId.substring(0, 12) + "...";
        }

        String description = session.description() == null ? "" : session.description().trim();
        if (description.isEmpty()) {
            return session.name() + "  (" + shortId + ")";
        }
        String compactDescription = description.length() > 36 ? description.substring(0, 36) + "..." : description;
        return session.name() + "  (" + shortId + ")\n" + compactDescription;
    }

    private static String compactErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String normalized = message.replace('\n', ' ').trim();
        if (normalized.length() > 120) {
            return normalized.substring(0, 120) + "...";
        }
        return normalized;
    }

    private static ShapeDrawable roundedRect(View view, int color, int radiusDp) {
        var background = new ShapeDrawable();
        background.setShape(ShapeDrawable.RECTANGLE);
        background.setCornerRadius(view.dp(radiusDp));
        background.setColor(color);
        return background;
    }

    private static LinearLayout.LayoutParams withBottomMargin(LinearLayout.LayoutParams params, int marginBottom) {
        params.bottomMargin = marginBottom;
        return params;
    }

    private static void setStatus(TextView statusView, String text, int color) {
        statusView.setText(text);
        statusView.setTextColor(color);
    }

    private static String currentSessionText() {
        String sessionId = SessionStore.getSessionId();
        if (sessionId.isEmpty()) {
            return "Current Session ID: (none)";
        }
        return "Current Session ID: " + sessionId;
    }
}
