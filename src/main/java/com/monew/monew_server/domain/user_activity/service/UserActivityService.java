package com.monew.monew_server.domain.user_activity.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import com.monew.monew_server.domain.user_activity.dto.UserActivityDto;
import com.monew.monew_server.domain.user_activity.dto.UserInfoDto;
import com.monew.monew_server.domain.user_activity.mapper.UserActivityMapper;
import com.monew.monew_server.domain.user_activity.repository.UserActivityCommentLikeRepository;
import com.monew.monew_server.domain.user_activity.repository.UserActivityCommentRepository;
import com.monew.monew_server.domain.user_activity.repository.UserActivityQueryRepository;
import com.monew.monew_server.domain.user_activity.repository.UserActivitySubscriptionRepository;
import com.monew.monew_server.exception.ErrorCode;
import com.monew.monew_server.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserActivityService {

	private final UserRepository userRepository;
	private final UserActivityMapper userActivityMapper;
	private final UserActivitySubscriptionRepository userActivitySubscriptionRepository;
	private final UserActivityQueryRepository userActivityQueryRepository;
	private final UserActivityCommentRepository userActivityCommentRepository;
	private final UserActivityCommentLikeRepository userActivityCommentLikeRepository;

	// 사용자 기본 정보 조회 (닉네임, 이메일)
	public UserInfoDto getUserInfo(UUID userId) {
		// 사용자 조회
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: " + userId));

		return userActivityMapper.toUserInfoDto(user);
	}

	// 사용자가 구독중인 관심사 조회
	public UserActivityDto getUserActivity(UUID userId) {
		// 사용자 정보 가져오기
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: " + userId));

		// 구독 중인 관심사 10개 조회
		List<String> subscriptions = userActivitySubscriptionRepository
			.findTop10ByUserIdOrderByCreatedAtDesc(userId)
			.stream()
			.map(s -> s.getInterest().getName())
			.toList();

		// 최근 작성한 댓글
		List<String> comments = userActivityCommentRepository
			.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
			.stream()
			.map(c -> c.getContent())
			.toList();

		// 최근 좋아요한 댓글
		List<String> commentLikes = userActivityCommentLikeRepository
			.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
			.stream()
			.map(cl ->cl.getComment().getContent())
			.toList();


		return userActivityMapper.toDto(
			user,
			subscriptions,
			comments,
			commentLikes,
			List.of()
			);
	}


}
