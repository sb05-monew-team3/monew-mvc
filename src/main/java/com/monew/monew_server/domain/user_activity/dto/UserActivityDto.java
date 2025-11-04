package com.monew.monew_server.domain.user_activity.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@JsonPropertyOrder({
	"id", "email", "nickname", "createdAt",
	"subscriptions", "comments", "commentLikes", "articleViews"
})
public class UserActivityDto {

	private UUID id;
	private String email;
	private String nickname;
	private Instant createdAt;

	private List<SubscriptionSummaryDto> subscriptions;
	private List<CommentSummaryDto> comments;
	private List<CommentLikeSummaryDto> commentLikes;
	private List<ArticleViewSummaryDto> articleViews;


}
