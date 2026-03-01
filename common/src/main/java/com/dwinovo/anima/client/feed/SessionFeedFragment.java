package com.dwinovo.anima.client.feed;

import com.dwinovo.anima.telemetry.api.AnimaApiClient;
import com.dwinovo.anima.telemetry.model.SessionSnapshotResponse;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public final class SessionFeedFragment extends Fragment {

    private static final String REQUEST_SOURCE = "client-session-feed";
    private static final String ARG_SESSION_ID = "sessionId";
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_SESSION = 0xFFB0B0B0;
    private static final int COLOR_SUMMARY = 0xFFA0E0FF;
    private static final int COLOR_LOADING = 0xFFFFE29A;
    private static final int COLOR_ERROR = 0xFFFF7070;
    private static final int COLOR_BODY = 0xFFEDEDED;
    private static final int COLOR_FOOTER = 0xFF909090;

    private String sessionId;

    private TextView sessionView;
    private TextView summaryView;
    private TextView contentView;
    private Button refreshButton;

    private SessionSnapshotResponse.SessionSnapshotData snapshot;
    private String errorMessage;
    private boolean loading;
    private int requestVersion;

    public SessionFeedFragment() {
    }

    @Override
    public void onCreate(@Nullable DataSet savedInstanceState) {
        super.onCreate(savedInstanceState);
        DataSet args = getArguments();
        sessionId = args == null ? null : args.getString(ARG_SESSION_ID);
    }

    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable DataSet savedInstanceState
    ) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int outerPadding = root.dp(12);
        root.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);

        TextView titleView = new TextView(requireContext());
        titleView.setText("Anima Feed");
        titleView.setTextSize(18);
        titleView.setTextColor(COLOR_TITLE);
        root.addView(titleView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        sessionView = new TextView(requireContext());
        sessionView.setTextSize(13);
        sessionView.setTextColor(COLOR_SESSION);
        root.addView(sessionView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(requireContext());
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        summaryView = new TextView(requireContext());
        summaryView.setTextSize(13);
        summaryView.setTextColor(COLOR_SUMMARY);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
        actionRow.addView(summaryView, summaryParams);

        refreshButton = new Button(requireContext());
        refreshButton.setText("Refresh");
        refreshButton.setOnClickListener((__) -> requestSnapshot());
        actionRow.addView(refreshButton, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        root.addView(actionRow, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(requireContext());
        contentView = new TextView(requireContext());
        contentView.setTextSize(13);
        contentView.setTextColor(COLOR_BODY);
        contentView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        contentView.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        int innerPadding = contentView.dp(6);
        contentView.setPadding(innerPadding, innerPadding, innerPadding, innerPadding);
        scrollView.addView(contentView, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.START));
        root.addView(scrollView, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));

        TextView footerView = new TextView(requireContext());
        footerView.setText("Press Esc to close. Use mouse wheel to scroll.");
        footerView.setTextSize(12);
        footerView.setTextColor(COLOR_FOOTER);
        root.addView(footerView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        renderState();
        requestSnapshot();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requestVersion++;
        sessionView = null;
        summaryView = null;
        contentView = null;
        refreshButton = null;
    }

    private void requestSnapshot() {
        if (sessionId == null || sessionId.isBlank()) {
            loading = false;
            snapshot = null;
            errorMessage = "No session_id available. Open a local world first.";
            renderState();
            return;
        }

        loading = true;
        snapshot = null;
        errorMessage = null;
        int currentRequestVersion = ++requestVersion;
        renderState();

        AnimaApiClient.getSessionSnapshot(sessionId, REQUEST_SOURCE).whenComplete((data, throwable) ->
            Core.executeOnUiThread(() -> {
                if (currentRequestVersion != requestVersion) {
                    return;
                }

                loading = false;
                if (throwable != null) {
                    snapshot = null;
                    errorMessage = "Snapshot request failed: " + safe(throwable.getMessage());
                    renderState();
                    return;
                }

                if (data == null) {
                    snapshot = null;
                    errorMessage = "Snapshot request failed. Check backend status or session_id.";
                    renderState();
                    return;
                }

                snapshot = data;
                errorMessage = null;
                renderState();
            })
        );
    }

    private void renderState() {
        if (sessionView == null || summaryView == null || contentView == null || refreshButton == null) {
            return;
        }

        sessionView.setText("Session: " + SessionFeedFormatter.displaySessionId(sessionId));
        refreshButton.setEnabled(!loading);

        if (loading) {
            summaryView.setText("Loading snapshot...");
            summaryView.setTextColor(COLOR_LOADING);
            contentView.setText("");
            return;
        }

        if (errorMessage != null) {
            summaryView.setText(errorMessage);
            summaryView.setTextColor(COLOR_ERROR);
            contentView.setText("");
            return;
        }

        if (snapshot == null) {
            summaryView.setText("No snapshot data.");
            summaryView.setTextColor(COLOR_ERROR);
            contentView.setText("");
            return;
        }

        summaryView.setText(SessionFeedFormatter.summaryLine(snapshot));
        summaryView.setTextColor(COLOR_SUMMARY);
        contentView.setText(SessionFeedFormatter.feedBody(snapshot));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
