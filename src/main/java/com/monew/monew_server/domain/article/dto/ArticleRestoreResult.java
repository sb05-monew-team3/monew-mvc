package com.monew.monew_server.domain.article.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ArticleRestoreResult(
	LocalDateTime restoreDate,
	List<UUID> restoredArticleIds,
	int restoredArticleCount
) {
}