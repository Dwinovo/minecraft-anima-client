package com.dwinovo.anima.client.feed;

import com.dwinovo.anima.telemetry.model.SessionSnapshotResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class SessionFeedFormatterTest {

    @Test
    void formatsSnapshotSummaryAndFeedBody() {
        SessionSnapshotResponse.CommentSnapshot comment = new SessionSnapshotResponse.CommentSnapshot(
            "comment_1",
            "post_1",
            1,
            "2026-03-01T10:20:00Z",
            "好主意！",
            "user_2",
            "Bob"
        );
        SessionSnapshotResponse.PostSnapshot post = new SessionSnapshotResponse.PostSnapshot(
            "post_1",
            "2026-03-01T10:00:00Z",
            "第一条帖子",
            "user_1",
            "Alice",
            2,
            List.of(comment)
        );
        SessionSnapshotResponse.SessionSnapshotData data = new SessionSnapshotResponse.SessionSnapshotData(
            "session_1",
            "2026-03-01T10:30:00Z",
            1,
            1,
            2,
            Map.of(),
            Map.of(
                "post_1",
                List.of(
                    new SessionSnapshotResponse.PostLike("user_3", "Carol", "2026-03-01T10:10:00Z"),
                    new SessionSnapshotResponse.PostLike("user_4", "Dave", "2026-03-01T10:12:00Z")
                )
            ),
            List.of(post)
        );

        String summary = SessionFeedFormatter.summaryLine(data);
        String body = SessionFeedFormatter.feedBody(data);

        assertTrue(summary.contains("posts=1"));
        assertTrue(summary.contains("comments=1"));
        assertTrue(summary.contains("likes=2"));

        assertTrue(body.contains("POST post_1  by Alice  at 2026-03-01T10:00:00Z"));
        assertTrue(body.contains("第一条帖子"));
        assertTrue(body.contains("Liked by: Carol, Dave"));
        assertTrue(body.contains("[Bob] 2026-03-01T10:20:00Z"));
        assertTrue(body.contains("好主意！"));
    }
}
