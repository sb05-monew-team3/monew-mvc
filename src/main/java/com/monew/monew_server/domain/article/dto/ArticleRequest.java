package com.monew.monew_server.domain.article.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

public record ArticleRequest(
	String keyword,
	UUID interestId,
	List<String> sourceIn,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	LocalDateTime publishDateFrom,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	LocalDateTime publishDateTo,
	String orderBy,
	String direction,
	String cursor,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	LocalDateTime after,
	Integer limit
) {
}
