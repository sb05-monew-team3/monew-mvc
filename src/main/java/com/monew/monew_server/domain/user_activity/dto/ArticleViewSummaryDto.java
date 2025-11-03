package com.monew.monew_server.domain.user_activity.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ArticleViewSummaryDto {
	private UUID id;
	private UUID viewedBy;
	private OffsetDateTime createdAt;
	private UUID articleId;
	private String source;
	private String sourceUrl;
	private String articleTitle;
	private OffsetDateTime articlePublishDate;
	private String articleSummary;
	private long articleCommentCount;
	private long articleViewCount;
}
