package com.monew.monew_server.domain.user_activity.mapper;

import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user_activity.dto.ArticleViewSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.CommentLikeSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.CommentSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.SubscriptionSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.UserActivityDto;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}
