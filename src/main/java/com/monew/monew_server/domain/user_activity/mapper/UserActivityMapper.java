package com.monew.monew_server.domain.user_activity.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user_activity.dto.UserActivityDto;
import com.monew.monew_server.domain.user_activity.dto.UserInfoDto;

@Mapper(componentModel = "spring")
public interface UserActivityMapper {

	UserActivityMapper INSTANCE = Mappers.getMapper(UserActivityMapper.class);

	@Mapping(target = "id", source = "user.id")
	@Mapping(target = "email", source = "user.email")
	@Mapping(target = "nickname", source = "user.nickname")
	@Mapping(target = "createdAt", expression = "java(user.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))")
	@Mapping(target = "subscriptions", source = "subscriptions")
	@Mapping(target = "comments", source = "comments")
	@Mapping(target = "commentLikes", source = "commentLikes")
	@Mapping(target = "articleViews", source = "articleViews")
	UserActivityDto toDto(
		User user,
		List<String> subscriptions,
		List<String> comments,
		List<String> commentLikes,
		List<String> articleViews
	);

	@Mapping(target = "nickname", source ="nickname")
	@Mapping(target = "email" , source = "email")
	UserInfoDto toUserInfoDto(User user);




}
