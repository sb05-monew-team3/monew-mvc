package com.monew.monew_server.domain.comment.repository;

import com.monew.monew_server.domain.comment.entity.Comment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// (인터페이스)
public interface CommentRepositoryCustom {

    List<Comment> findByArticleIdWithCursor(
            UUID articleId, // 어떤 기사인지
            String orderBy, // 정렬 기준
            String direction, //
            String cursor,
            Instant after,
            int limit
    );
}
