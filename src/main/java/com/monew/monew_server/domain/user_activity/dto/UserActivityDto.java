package com.monew.monew_server.domain.user_activity.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class UserActivityDto {

	private UUID id;
	private String email;
	private String nickname;
	private OffsetDateTime createdAt;

	private List<String> subscriptions;
	private List<String> comments;
	private List<String> commentLikes;
	private List<String> articleViews;


}
