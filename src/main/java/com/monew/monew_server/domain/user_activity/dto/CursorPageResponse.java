package com.monew.monew_server.domain.user_activity.dto;

import java.time.Instant;
import java.util.List;

public record CursorPageResponse<T> (
	List<T> content,
	String nextCursor,
	Instant nextAfter,
	int size,
	long totalElements,
	boolean hasNext
){}
