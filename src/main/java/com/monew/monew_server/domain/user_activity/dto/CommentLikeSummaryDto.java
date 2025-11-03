package com.monew.monew_server.domain.user_activity.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommentLikeSummaryDto {
	private UUID id;
	private OffsetDateTime createdAt;
	private UUID commentId;
	private UUID articleId;
	private String articleTitle;
	private UUID commentUserId;
	private String commentUserNickname;
	private String commentContent;
	private long commentLikeCount;
	private OffsetDateTime commentCreatedAt;
}
