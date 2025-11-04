package com.monew.monew_server.domain.user_activity.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.comment.entity.CommentLike;

@Repository
public interface UserActivityCommentLikeCountRepository extends JpaRepository<CommentLike, UUID> {
	
}
