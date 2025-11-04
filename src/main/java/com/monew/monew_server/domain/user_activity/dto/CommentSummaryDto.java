package com.monew.monew_server.domain.user_activity.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommentSummaryDto {
	private UUID id;
	private UUID articleId;
	private String articleTitle;
	private UUID userId;
	private String userNickname;
	private String content;
	private long likeCount;
	private Instant createdAt;
}