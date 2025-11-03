package com.monew.monew_server.domain.user_activity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.comment.entity.CommentLike;

@Repository
public interface UserActivityCommentLikeRepository extends JpaRepository<CommentLike, UUID> {
	// 특정 사용자가 좋아요한 댓글 10
	@EntityGraph(attributePaths = {"comment"})
	List<CommentLike> findTop10ByUser_IdOrderByCreatedAtDesc(UUID userId);

	long countByComment_Id(UUID commentId);
}
