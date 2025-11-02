package com.monew.monew_server.domain.user_activity.repository;

import com.monew.monew_server.domain.comment.entity.Comment;
import com.monew.monew_server.domain.interest.entity.Interest;
import com.monew.monew_server.domain.article.entity.ArticleView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserActivityQueryRepository {

	// 관심사
	@Query("""
        SELECT i
        FROM Interest i
        WHERE i.createdAt = :userId
          AND (:cursor IS NULL OR i.createdAt < :cursor)
        ORDER BY i.createdAt DESC
    """)
	List<Interest> findInterestsByUserIdWithCursor(@Param("userId") UUID userId,
		@Param("cursor") Instant cursor,
		Pageable pageable);

	// 댓글
	@Query("""
        SELECT c
        FROM Comment c
        WHERE c.user.id = :userId
          AND c.deletedAt IS NULL
          AND (:cursor IS NULL OR c.createdAt < :cursor)
        ORDER BY c.createdAt DESC
    """)
	List<Comment> findCommentsByUserIdWithCursor(@Param("userId") UUID userId,
		@Param("cursor") Instant cursor,
		Pageable pageable);

	// 기사 조회
	@Query("""
        SELECT av
        FROM ArticleView av
        WHERE av.user.id = :userId
          AND (:cursor IS NULL OR av.createdAt < :cursor)
        ORDER BY av.createdAt DESC
    """)
	List<ArticleView> findArticleViewsByUserIdWithCursor(@Param("userId") UUID userId,
		@Param("cursor") Instant cursor,
		Pageable pageable);
}