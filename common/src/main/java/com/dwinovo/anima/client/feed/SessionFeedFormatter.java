package com.dwinovo.anima.client.feed;

import com.dwinovo.anima.telemetry.model.SessionSnapshotResponse;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class SessionFeedFormatter {

    private SessionFeedFormatter() {}

    static String summaryLine(SessionSnapshotResponse.SessionSnapshotData snapshot) {
        if (snapshot == null) {
            return "";
        }
        return "Generated: " + safe(snapshot.generated_at())
            + "  posts=" + snapshot.total_posts()
            + "  comments=" + snapshot.total_comments()
            + "  likes=" + snapshot.total_likes();
    }

    static String feedBody(SessionSnapshotResponse.SessionSnapshotData data) {
        if (data == null) {
            return "";
        }
        if (data.posts().isEmpty()) {
            return "No posts yet.";
        }

        StringBuilder builder = new StringBuilder();
        for (SessionSnapshotResponse.PostSnapshot post : data.posts()) {
            String postAuthor = firstNonBlank(post.author_name(), post.author_id(), "unknown");
            builder.append("POST ")
                .append(safe(post.post_id()))
                .append("  by ")
                .append(postAuthor)
                .append("  at ")
                .append(safe(post.timestamp()))
                .append('\n');
            builder.append(safe(post.content())).append('\n');

            builder.append("Likes: ").append(post.like_count()).append('\n');
            List<SessionSnapshotResponse.PostLike> likes = data.likes_by_post().get(post.post_id());
            List<SessionSnapshotResponse.PostLike> safeLikes = likes == null ? Collections.emptyList() : likes;
            if (!safeLikes.isEmpty()) {
                String likeActors = safeLikes.stream()
                    .map(like -> firstNonBlank(like.actor_name(), like.actor_id(), "unknown"))
                    .filter(Objects::nonNull)
                    .limit(8)
                    .collect(Collectors.joining(", "));
                builder.append("Liked by: ").append(likeActors).append('\n');
            }

            if (post.comments().isEmpty()) {
                builder.append("No comments.").append('\n');
            } else {
                builder.append("Comments:").append('\n');
                for (SessionSnapshotResponse.CommentSnapshot comment : post.comments()) {
                    int depth = Math.max(1, comment.depth());
                    int indent = 2 + depth * 2;
                    builder.append(" ".repeat(indent))
                        .append('[')
                        .append(firstNonBlank(comment.author_name(), comment.author_id(), "unknown"))
                        .append("] ")
                        .append(safe(comment.timestamp()))
                        .append('\n');
                    builder.append(" ".repeat(indent))
                        .append(safe(comment.content()))
                        .append('\n');
                }
            }

            builder.append('\n');
        }
        return builder.toString().trim();
    }

    static String displaySessionId(String sessionId) {
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
}
