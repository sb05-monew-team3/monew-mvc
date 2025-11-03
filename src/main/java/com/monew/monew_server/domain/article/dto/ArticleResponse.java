package com.monew.monew_server.domain.article.dto;

import java.time.Instant;
import java.util.UUID;

import com.monew.monew_server.domain.article.entity.ArticleSource;

public record ArticleResponse(
	UUID id,
	ArticleSource source,
	String sourceUrl,
	String title,
	Instant publishDate,
	String summary,
	Long commentCount,
	Long viewCount,
	Boolean viewedByMe
) {
}
