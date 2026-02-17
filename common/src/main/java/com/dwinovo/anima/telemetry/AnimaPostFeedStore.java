package com.dwinovo.anima.telemetry;

import com.dwinovo.anima.telemetry.model.EventResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnimaPostFeedStore {

    private static final Map<String, EventResponse.PostResponse> POSTS_BY_ID = new LinkedHashMap<>();

    private AnimaPostFeedStore() {
    }

    public static synchronized List<EventResponse.PostResponse> addAll(List<EventResponse.PostResponse> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }

        List<EventResponse.PostResponse> newlyAdded = new ArrayList<>();
        for (EventResponse.PostResponse post : posts) {
            if (post == null || post.post_id() == null || POSTS_BY_ID.containsKey(post.post_id())) {
                continue;
            }
            POSTS_BY_ID.put(post.post_id(), post);
            newlyAdded.add(post);
        }
        return newlyAdded;
    }

    public static synchronized List<EventResponse.PostResponse> snapshot() {
        return List.copyOf(POSTS_BY_ID.values());
    }
}
