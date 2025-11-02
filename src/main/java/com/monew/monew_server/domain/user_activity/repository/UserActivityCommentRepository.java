package com.monew.monew_server.domain.user_activity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.monew.monew_server.domain.comment.entity.Comment;

public interface UserActivityCommentRepository extends JpaRepository<Comment, UUID> {
	// 특정 사용자가 작성한 최근 댓글 10개 조회
	List<Comment> findTop10ByUser_IdOrderByCreatedAtDesc(UUID userId);
}
