package com.monew.monew_server.domain.user_activity.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ArticleViewSummaryDto {
	private UUID id;
	private UUID viewedBy;
	private Instant createdAt;
	private UUID articleId;
	private String source;
	private String sourceUrl;
	private String articleTitle;
	private Instant articlePublishedDate;
	private String articleSummary;
	private long articleCommentCount;
	private long articleViewCount;
}
