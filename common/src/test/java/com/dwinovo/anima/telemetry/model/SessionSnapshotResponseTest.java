package com.dwinovo.anima.telemetry.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionSnapshotResponseTest {

    @Test
    void defaultsCollectionsToEmptyWhenNull() {
        SessionSnapshotResponse.SessionSnapshotData data = new SessionSnapshotResponse.SessionSnapshotData(
            "s_world",
            "2026-02-28T10:00:00+00:00",
            0,
            0,
            0,
            null,
            null,
            null
        );

        assertNotNull(data.actors());
        assertNotNull(data.likes_by_post());
        assertNotNull(data.posts());
        assertEquals(0, data.actors().size());
        assertEquals(0, data.likes_by_post().size());
        assertEquals(0, data.posts().size());
    }

    @Test
    void defaultsPostCommentsToEmptyWhenNull() {
        SessionSnapshotResponse.PostSnapshot post = new SessionSnapshotResponse.PostSnapshot(
            "post_1",
            "2026-02-28T10:00:00+00:00",
            "hello",
            "a1",
            "Alice",
            0,
            null
        );

        assertNotNull(post.comments());
        assertEquals(0, post.comments().size());
    }
}
