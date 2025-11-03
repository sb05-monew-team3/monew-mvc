package com.monew.monew_server.domain.user_activity.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubscriptionSummaryDto {
	private UUID id;
	private UUID interestId;
	private String interestName;
	private List<String> interestKeywords;
	private long interestSubscriberCount;
	private Instant createdAt;
}
