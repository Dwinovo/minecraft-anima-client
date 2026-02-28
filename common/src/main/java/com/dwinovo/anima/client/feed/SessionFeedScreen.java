package com.dwinovo.anima.client.feed;

import com.dwinovo.anima.telemetry.api.AnimaApiClient;
import com.dwinovo.anima.telemetry.model.SessionSnapshotResponse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SessionFeedScreen extends Screen {

    private static final String REQUEST_SOURCE = "client-session-feed";
    private static final int PANEL_PADDING = 12;
    private static final int SCROLL_STEP = 16;
    private static final int LINE_SPACING = 2;
    private static final int BACKGROUND_COLOR = 0xFF0F1115;
    private static final int PANEL_COLOR = 0xFF171B22;
    private static final int PANEL_BORDER_COLOR = 0xFF2A3342;

    private final String sessionId;
    private SessionSnapshotResponse.SessionSnapshotData snapshot;
    private String errorMessage;
    private boolean loading;
    private int scrollOffset;
    private int maxScrollOffset;
    private int requestVersion;

    public SessionFeedScreen(String sessionId) {
        super(Component.literal("Anima Feed"));
        this.sessionId = sessionId;
    }

    @Override
    protected void init() {
        requestSnapshot();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            requestSnapshot();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScrollOffset <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollOffset = Mth.clamp((int) (scrollOffset - scrollY * SCROLL_STEP), 0, maxScrollOffset);
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Use opaque background to avoid the blurred world effect behind the feed.
        guiGraphics.fill(0, 0, width, height, BACKGROUND_COLOR);

        int left = PANEL_PADDING;
        int right = width - PANEL_PADDING;
        int top = PANEL_PADDING;
        int bottom = height - PANEL_PADDING;
        guiGraphics.fill(left - 5, top - 5, right + 5, bottom + 5, PANEL_BORDER_COLOR);
        guiGraphics.fill(left - 4, top - 4, right + 4, bottom + 4, PANEL_COLOR);

        int y = top;
        guiGraphics.drawString(font, "Anima Feed", left, y, 0xFFFFFF, false);
        y += font.lineHeight + LINE_SPACING;
        guiGraphics.drawString(font, "Session: " + displaySessionId(), left, y, 0xB0B0B0, false);
        y += font.lineHeight + LINE_SPACING;

        if (loading) {
            guiGraphics.drawString(font, "Loading snapshot...", left, y, 0xFFE29A, false);
            drawFooter(guiGraphics, left, bottom);
            return;
        }

        if (errorMessage != null) {
            drawWrapped(guiGraphics, errorMessage, left, y, right - left, 0xFF7070);
            drawFooter(guiGraphics, left, bottom);
            return;
        }

        if (snapshot == null) {
            guiGraphics.drawString(font, "No snapshot data.", left, y, 0xFF7070, false);
            drawFooter(guiGraphics, left, bottom);
            return;
        }

        guiGraphics.drawString(
            font,
            "Generated: " + safe(snapshot.generated_at()) + "  posts=" + snapshot.total_posts() + "  comments=" + snapshot.total_comments() + "  likes=" + snapshot.total_likes(),
            left,
            y,
            0xA0E0FF,
            false
        );
        y += font.lineHeight + LINE_SPACING + 2;

        int contentTop = y;
        int contentBottom = bottom - font.lineHeight - 4;
        int contentWidth = right - left;
        List<FeedLine> lines = buildFeedLines(snapshot);
        int lineHeight = font.lineHeight + LINE_SPACING;
        int contentHeight = measureHeight(lines, contentWidth, lineHeight);
        maxScrollOffset = Math.max(0, contentHeight - (contentBottom - contentTop));
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScrollOffset);

        guiGraphics.enableScissor(left, contentTop, right, contentBottom);
        drawLines(guiGraphics, lines, left, contentTop, contentBottom, contentWidth, lineHeight);
        guiGraphics.disableScissor();

        drawFooter(guiGraphics, left, bottom);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void requestSnapshot() {
        if (sessionId == null || sessionId.isBlank()) {
            loading = false;
            snapshot = null;
            errorMessage = "No session_id available. Open a local world first.";
            return;
        }

        loading = true;
        snapshot = null;
        errorMessage = null;
        scrollOffset = 0;
        maxScrollOffset = 0;
        int currentRequestVersion = ++requestVersion;

        AnimaApiClient.getSessionSnapshot(sessionId, REQUEST_SOURCE).whenComplete((data, throwable) ->
            Minecraft.getInstance().execute(() -> {
                if (currentRequestVersion != requestVersion) {
                    return;
                }

                loading = false;
                if (throwable != null) {
                    snapshot = null;
                    errorMessage = "Snapshot request failed: " + safe(throwable.getMessage());
                    return;
                }

                if (data == null) {
                    snapshot = null;
                    errorMessage = "Snapshot request failed. Check backend status or session_id.";
                    return;
                }

                snapshot = data;
                errorMessage = null;
            })
        );
    }

    private void drawLines(
        GuiGraphics guiGraphics,
        List<FeedLine> lines,
        int left,
        int contentTop,
        int contentBottom,
        int contentWidth,
        int lineHeight
    ) {
        int y = contentTop - scrollOffset;
        for (FeedLine line : lines) {
            int indent = Math.max(0, line.indent());
            int wrapWidth = Math.max(20, contentWidth - indent);
            List<FormattedCharSequence> wrapped = wrapLine(line.text(), wrapWidth);
            for (FormattedCharSequence wrappedPart : wrapped) {
                if (y + lineHeight >= contentTop && y <= contentBottom) {
                    guiGraphics.drawString(font, wrappedPart, left + indent, y, line.color(), false);
                }
                y += lineHeight;
            }
        }
    }

    private int measureHeight(List<FeedLine> lines, int contentWidth, int lineHeight) {
        int totalHeight = 0;
        for (FeedLine line : lines) {
            int indent = Math.max(0, line.indent());
            int wrapWidth = Math.max(20, contentWidth - indent);
            List<FormattedCharSequence> wrapped = wrapLine(line.text(), wrapWidth);
            totalHeight += wrapped.size() * lineHeight;
        }
        return totalHeight;
    }

    private List<FormattedCharSequence> wrapLine(String text, int width) {
        if (text == null || text.isBlank()) {
            return Collections.singletonList(FormattedCharSequence.EMPTY);
        }
        return new ArrayList<>(font.split(Component.literal(text), width));
    }

    private List<FeedLine> buildFeedLines(SessionSnapshotResponse.SessionSnapshotData data) {
        List<FeedLine> lines = new ArrayList<>();
        Map<String, List<SessionSnapshotResponse.PostLike>> likesByPost = data.likes_by_post();

        if (data.posts().isEmpty()) {
            lines.add(new FeedLine("No posts yet.", 0, 0xB0B0B0));
            return lines;
        }

        for (SessionSnapshotResponse.PostSnapshot post : data.posts()) {
            String postAuthor = firstNonBlank(post.author_name(), post.author_id(), "unknown");
            lines.add(new FeedLine("POST " + safe(post.post_id()) + "  by " + postAuthor + "  at " + safe(post.timestamp()), 0, 0xFFD27F));
            lines.add(new FeedLine(safe(post.content()), 0, 0xFFFFFF));

            List<SessionSnapshotResponse.PostLike> likes = likesByPost.get(post.post_id());
            List<SessionSnapshotResponse.PostLike> safeLikes = likes == null ? Collections.emptyList() : likes;
            lines.add(new FeedLine("Likes: " + post.like_count(), 0, 0xA0FFA0));
            if (!safeLikes.isEmpty()) {
                String likeActors = safeLikes.stream()
                    .map(like -> firstNonBlank(like.actor_name(), like.actor_id(), "unknown"))
                    .filter(Objects::nonNull)
                    .limit(8)
                    .collect(Collectors.joining(", "));
                lines.add(new FeedLine("Liked by: " + likeActors, 12, 0x80C080));
            }

            if (post.comments().isEmpty()) {
                lines.add(new FeedLine("No comments.", 12, 0x9A9A9A));
            } else {
                lines.add(new FeedLine("Comments:", 12, 0x80C0FF));
                for (SessionSnapshotResponse.CommentSnapshot comment : post.comments()) {
                    int depth = Math.max(1, comment.depth());
                    int indent = 12 + depth * 14;
                    String commentAuthor = firstNonBlank(comment.author_name(), comment.author_id(), "unknown");
                    lines.add(new FeedLine("[" + commentAuthor + "] " + safe(comment.timestamp()), indent, 0xC8D8FF));
                    lines.add(new FeedLine(safe(comment.content()), indent, 0xE6E6E6));
                }
            }
            lines.add(new FeedLine("", 0, 0xFFFFFF));
        }

        return lines;
    }

    private void drawFooter(GuiGraphics guiGraphics, int left, int bottom) {
        guiGraphics.drawString(
            font,
            "[Mouse Wheel] Scroll   [R] Refresh   [Esc] Close",
            left,
            bottom - font.lineHeight,
            0x909090,
            false
        );
    }

    private void drawWrapped(GuiGraphics guiGraphics, String text, int x, int y, int width, int color) {
        int lineHeight = font.lineHeight + LINE_SPACING;
        for (FormattedCharSequence line : wrapLine(text, width)) {
            guiGraphics.drawString(font, line, x, y, color, false);
            y += lineHeight;
        }
    }

    private String displaySessionId() {
        return sessionId == null || sessionId.isBlank() ? "<unavailable>" : sessionId;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private record FeedLine(String text, int indent, int color) {}
}
