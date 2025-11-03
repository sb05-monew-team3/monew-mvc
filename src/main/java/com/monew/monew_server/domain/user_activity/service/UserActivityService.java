package com.monew.monew_server.domain.user_activity.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import com.monew.monew_server.domain.user_activity.dto.UserActivityDto;
import com.monew.monew_server.domain.user_activity.dto.UserInfoDto;
import com.monew.monew_server.domain.user_activity.mapper.UserActivityMapper;
import com.monew.monew_server.domain.user_activity.repository.UserActivityArticleViewRepository;
import com.monew.monew_server.domain.user_activity.repository.UserActivityCommentLikeRepository;
import com.monew.monew_server.domain.user_activity.repository.UserActivityCommentRepository;
import com.monew.monew_server.domain.user_activity.repository.UserActivitySubscriptionRepository;
import com.monew.monew_server.exception.ErrorCode;
import com.monew.monew_server.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityService {

	private final UserRepository userRepository;
	private final UserActivityMapper userActivityMapper;
	private final UserActivitySubscriptionRepository userActivitySubscriptionRepository;
	private final UserActivityCommentRepository userActivityCommentRepository;
	private final UserActivityCommentLikeRepository userActivityCommentLikeRepository;
	private final UserActivityArticleViewRepository userActivityArticleViewRepository;

	/*
	 * 사용자 기본 정보 조회
	 * (이메일, 닉네임)
	 */
	@Transactional(readOnly = true)
	public UserInfoDto getUserInfo(UUID userId) {
		log.info("[UserActivityService] 사용자 기본 정보 조회 요청 - userId={}", userId);

		User user = userRepository.findById(userId)
			.orElseThrow(() -> {
				log.warn("[UserActivityService] 존재하지 않는 사용자 ID 요청 - userId={}", userId);
				return new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: " + userId);
			});

		UserInfoDto dto = userActivityMapper.toUserInfoDto(user);
		log.debug("[UserActivityService] 사용자 정보 반환 - nickname={}, email={}", dto.nickname(), dto.email());
		return dto;
	}

	/**
	 * 사용자 전체 활동 조회
	 * (구독 -> 댓글 -> 좋아요 -> 기사 조회 순
	 */

	@Transactional(readOnly = true)
	public UserActivityDto getUserActivity(UUID userId) {
		log.info("[UserActivityService] 사용자 활동 조회 요청 - userId={}", userId);

		User user = userRepository.findById(userId)
			.orElseThrow(() -> {
				log.warn("[UserActivityService] 존재하지 않는 사용자 ID 요청 - userId={}", userId);
				return new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: " + userId);
			});

		// 구독 중인 관심사 10개 조회
		List<String> subscriptions = userActivitySubscriptionRepository
			.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
			.stream()
			.map(s -> s.getInterest().getName())
			.toList();
		log.debug("[UserActivityService] 구독 관심사 {}건 조회 완료", subscriptions.size());

		// 최근 작성한 댓글
		List<String> comments = userActivityCommentRepository
			.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
			.stream()
			.map(c -> c.getContent())
			.toList();
		log.debug("[UserActivityService] 댓글 {}건 조회 완료", comments.size());

		// 최근 좋아요한 댓글
		List<String> commentLikes = userActivityCommentLikeRepository
			.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
			.stream()
			.map(cl ->cl.getComment().getContent())
			.toList();
		log.debug("[UserActivityService] 댓글 좋아요 {}건 조회 완료", commentLikes.size());

		// 최근 본 뉴스 기사
		List<String> articleViews = userActivityArticleViewRepository
			.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
			.stream()
			.map(av ->av.getArticle().getTitle())
			.toList();
		log.debug("[UserActivityService] 기사 조회 {}건 완료", articleViews.size());


		UserActivityDto result = userActivityMapper.toDto(
			user,
			subscriptions,
			comments,
			commentLikes,
			articleViews
		);

		log.info("[UserActivityService] 사용자 활동 조회 완료 - userId={}, totalCount(subs+comments+likes+views)={}",
			userId,
			subscriptions.size() + comments.size() + commentLikes.size() + articleViews.size());

		return result;
	}
}
