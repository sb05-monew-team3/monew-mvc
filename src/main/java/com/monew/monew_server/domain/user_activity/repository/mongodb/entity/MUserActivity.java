package com.monew.monew_server.domain.user_activity.repository.mongodb.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

@Document(collection = "user_activity")
@Data
@Builder
public class MUserActivity {

	@Id
	private UUID userId;

	private String email;
	private String nickname;
	private Instant createdAt;

	@Builder.Default
	private List<Subscription> subscriptions = new ArrayList<>();
	@Builder.Default
	private List<Comment> comments = new ArrayList<>();
	@Builder.Default
	private List<CommentLike> commentLikes = new ArrayList<>();
	@Builder.Default
	private List<ArticleView> articleViews = new ArrayList<>();

	@Data
	@Builder
	public static class Subscription {
		private UUID id;
		private UUID interestId;
		private String interestName;
		private List<String> interestKeywords;
		private Integer interestSubscriberCount;
		private Instant createdAt;
	}

	@Data
	@Builder
	public static class Comment {
		private UUID id;
		private UUID articleId;
		private String articleTitle;
		private UUID userId;
		private String userNickname;
		private String content;
		private Integer likeCount;
		private Instant createdAt;
	}

	@Data
	@Builder
	public static class CommentLike {
		private UUID id;
		private Instant createdAt;
		private UUID commentId;
		private UUID articleId;
		private String articleTitle;
		private UUID commentUserId;
		private String commentUserNickname;
		private String commentContent;
		private Integer commentLikeCount;
		private Instant commentCreatedAt;
	}

	@Data
	@Builder
	public static class ArticleView {
		private UUID id;
		private UUID viewedBy;
		private Instant createdAt;
		private UUID articleId;
		private String source;
		private String sourceUrl;
		private String articleTitle;
		private Instant articlePublishedDate;
		private String articleSummary;
		private Integer articleCommentCount;
		private Integer articleViewCount;
	}
}