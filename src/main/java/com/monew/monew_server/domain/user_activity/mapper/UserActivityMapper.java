package com.monew.monew_server.domain.user_activity.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user_activity.dto.ArticleViewSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.CommentLikeSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.CommentSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.SubscriptionSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.UserActivityDto;
import com.monew.monew_server.domain.user_activity.repository.mongodb.entity.MUserActivity;

@Mapper(componentModel = "spring")
public interface UserActivityMapper {

	@Mapping(target = "id", source = "user.id")
	@Mapping(target = "email", source = "user.email")
	@Mapping(target = "nickname", source = "user.nickname")
	@Mapping(target = "createdAt", expression = "java(user.getCreatedAt())")
	@Mapping(target = "subscriptions", source = "subscriptions")
	@Mapping(target = "comments", source = "comments")
	@Mapping(target = "commentLikes", source = "commentLikes")
	@Mapping(target = "articleViews", source = "articleViews")
	UserActivityDto toDto(
		User user,
		List<SubscriptionSummaryDto> subscriptions,
		List<CommentSummaryDto> comments,
		List<CommentLikeSummaryDto> commentLikes,
		List<ArticleViewSummaryDto> articleViews
	);

	@Mapping(target = "id", source = "userId")
	UserActivityDto toDto(MUserActivity mUserActivity);

	@Mapping(target = "interestSubscriberCount", source = "interestSubscriberCount", qualifiedByName = "integerToLong")
	SubscriptionSummaryDto toSubscriptionSummaryDto(MUserActivity.Subscription subscription);

	@Mapping(target = "likeCount", source = "likeCount", qualifiedByName = "integerToLong")
	CommentSummaryDto toCommentSummaryDto(MUserActivity.Comment comment);

	@Mapping(target = "commentLikeCount", source = "commentLikeCount", qualifiedByName = "integerToLong")
	CommentLikeSummaryDto toCommentLikeSummaryDto(MUserActivity.CommentLike commentLike);

	@Mapping(target = "articleCommentCount", source = "articleCommentCount", qualifiedByName = "integerToLong")
	@Mapping(target = "articleViewCount", source = "articleViewCount", qualifiedByName = "integerToLong")
	ArticleViewSummaryDto toArticleViewSummaryDto(MUserActivity.ArticleView articleView);

	@Named("integerToLong")
	default long integerToLong(Integer value) {
		return value != null ? value.longValue() : 0L;
	}

}
