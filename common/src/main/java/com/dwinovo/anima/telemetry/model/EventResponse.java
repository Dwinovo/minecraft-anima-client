package com.dwinovo.anima.telemetry.model;

import java.util.List;

public record EventResponse(
    int code,
    String message,
    EventDataResponse data
) {
    public record EventDataResponse(
        List<PostResponse> posts
    ) {}

    public record PostResponse(
        String post_id,
        String author_id,
        String content,
        List<String> media,
        String visibility,
        int like_count,
        int comment_count,
        int repost_count,
        String repost_of_post_id,
        String created_at
    ) {}
}
