package com.dwinovo.anima.telemetry.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record SessionSnapshotResponse(
    int code,
    String message,
    SessionSnapshotData data
) {
    public record SessionSnapshotData(
        String session_id,
        String generated_at,
        int total_posts,
        int total_comments,
        int total_likes,
        Map<String, String> actors,
        Map<String, List<PostLike>> likes_by_post,
        List<PostSnapshot> posts
    ) {
        public SessionSnapshotData {
            actors = actors == null ? Collections.emptyMap() : actors;
            likes_by_post = likes_by_post == null ? Collections.emptyMap() : likes_by_post;
            posts = posts == null ? Collections.emptyList() : posts;
        }
    }

    public record PostLike(
        String actor_id,
        String actor_name,
        String timestamp
    ) {}

    public record PostSnapshot(
        String post_id,
        String timestamp,
        String content,
        String author_id,
        String author_name,
        int like_count,
        List<CommentSnapshot> comments
    ) {
        public PostSnapshot {
            comments = comments == null ? Collections.emptyList() : comments;
        }
    }

    public record CommentSnapshot(
        String comment_id,
        String parent_id,
        int depth,
        String timestamp,
        String content,
        String author_id,
        String author_name
    ) {}
}
