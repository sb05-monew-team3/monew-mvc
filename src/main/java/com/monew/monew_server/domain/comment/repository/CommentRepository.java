package com.monew.monew_server.domain.comment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.article.repository.projection.CommentCountProjection;
import com.monew.monew_server.domain.comment.entity.Comment;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID>, CommentRepositoryCustom {

	List<Comment> findByArticle_Id(UUID articleId);

	// 삭제되지 않은 댓글만 조회
	List<Comment> findByArticle_IdAndDeletedAtIsNull(UUID articleId);

	@Query("""
		    SELECT c.article.id AS articleId, COUNT(c) AS commentCount
		    FROM Comment c
		    WHERE c.article.id IN :articleIds AND c.deletedAt IS NULL
		    GROUP BY c.article.id
		""")
	List<CommentCountProjection> findCommentCountsByArticleIds(@Param("articleIds") List<UUID> articleIds);

	@Query("SELECT COUNT(c) FROM Comment c WHERE c.article.id = :articleId")
	long countByArticleId(@Param("articleId") UUID articleId);
}
