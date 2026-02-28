package com.dwinovo.anima.telemetry.api;

import com.dwinovo.anima.telemetry.model.SessionSnapshotResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimaApiClientSessionSnapshotTest {

    @Test
    void parsesSessionSnapshotResponse() {
        String body = """
            {
              "code": 0,
              "message": "session snapshot fetched",
              "data": {
                "session_id": "s_world",
                "generated_at": "2026-02-28T10:00:00+00:00",
                "total_posts": 1,
                "total_comments": 1,
                "total_likes": 1,
                "actors": {
                  "a1": "Alice#abcde"
                },
                "likes_by_post": {
                  "post_1": [
                    {
                      "actor_id": "a1",
                      "actor_name": "Alice#abcde",
                      "timestamp": "2026-02-28T10:00:01+00:00"
                    }
                  ]
                },
                "posts": [
                  {
                    "post_id": "post_1",
                    "timestamp": "2026-02-28T10:00:00+00:00",
                    "content": "hello",
                    "author_id": "a1",
                    "author_name": "Alice#abcde",
                    "like_count": 1,
                    "comments": [
                      {
                        "comment_id": "comment_1",
                        "parent_id": "post_1",
                        "depth": 1,
                        "timestamp": "2026-02-28T10:00:02+00:00",
                        "content": "nice",
                        "author_id": "a1",
                        "author_name": "Alice#abcde"
                      }
                    ]
                  }
                ]
              }
            }
            """;

        SessionSnapshotResponse.SessionSnapshotData data = AnimaApiClient.parseSessionSnapshotData(200, body);

        assertNotNull(data);
        assertEquals("s_world", data.session_id());
        assertEquals(1, data.total_posts());
        assertEquals(1, data.posts().size());
        assertEquals("post_1", data.posts().getFirst().post_id());
        assertEquals(1, data.likes_by_post().get("post_1").size());
    }

    @Test
    void rejectsInvalidSessionSnapshotResponse() {
        String body = """
            {
              "code": 0,
              "message": "session snapshot fetched",
              "data": {
                "generated_at": "2026-02-28T10:00:00+00:00"
              }
            }
            """;

        SessionSnapshotResponse.SessionSnapshotData data = AnimaApiClient.parseSessionSnapshotData(200, body);

        assertNull(data);
    }
}
