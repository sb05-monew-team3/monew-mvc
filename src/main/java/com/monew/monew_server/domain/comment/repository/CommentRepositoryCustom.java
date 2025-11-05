package com.monew.monew_server.domain.comment.repository;

import com.monew.monew_server.domain.comment.entity.Comment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CommentRepositoryCustom {

    List<Comment> findByArticleIdWithCursor(
            UUID articleId,
            String orderBy,
            String direction,
            String cursor,
            Instant after,
            int limit
    );
}
