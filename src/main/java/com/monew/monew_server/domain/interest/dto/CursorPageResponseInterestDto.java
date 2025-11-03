package com.monew.monew_server.domain.interest.dto;

import java.time.Instant;
import java.util.List;

public record CursorPageResponseInterestDto(
    List<InterestDto> content,
    String nextCursor,
    Instant nextAfter,
    int size,
    Long totalElements,
    boolean hasNext
) {}
