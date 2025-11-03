package com.monew.monew_server.domain.user_activity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.comment.entity.Comment;

@Repository
public interface UserActivityCommentRepository extends JpaRepository<Comment, UUID> {
	// 특정 사용자가 작성한 최근 댓글 10개 조회
	@EntityGraph(attributePaths = {"user", "article"})
	List<Comment> findTop10ByUser_IdOrderByCreatedAtDesc(UUID userId);

	long countByArticle_Id(UUID articleId);
}
