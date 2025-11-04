package com.monew.monew_server.domain.notification.service;

import com.monew.monew_server.domain.comment.entity.Comment;
import com.monew.monew_server.domain.notification.dto.CursorPageResponse;
import com.monew.monew_server.domain.notification.dto.NotificationDto;
import com.monew.monew_server.domain.notification.entity.Notification;
import com.monew.monew_server.domain.notification.entity.NotificationResourceType;
import com.monew.monew_server.domain.notification.mapper.NotificationMapper;
import com.monew.monew_server.domain.notification.repository.NotificationRepository;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import com.monew.monew_server.exception.NotificationNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final NotificationMapper notificationMapper;
	private final UserRepository userRepository;

	@Transactional
	public void createCommentLikeNotification(Comment comment, UUID likedByUserId) {
		log.info("알림(댓글 좋아요) 생성 시작: commentId={}, likedBy={}", comment.getId(), likedByUserId);

		if (comment.getUser().getId().equals(likedByUserId)) {
			log.debug("자기 댓글 좋아요: commentId={}, userId={}", comment.getId(), likedByUserId);
			return;
		}


		User likedByUser = userRepository.getReferenceById(likedByUserId);

		Notification notification = Notification.builder()
			.user(comment.getUser())
			.content(String.format("%s님이 나의 댓글을 좋아합니다.", likedByUser.getNickname()))
			.resourceType(NotificationResourceType.comment)
			.resourceId(comment.getId())
			.confirmed(false)
			.build();

		notificationRepository.save(notification);

		log.info("알림(댓글 좋아요) 생성 완료: commentId={}, likedBy={}", comment.getId(), likedByUserId);
	}

	@Transactional(readOnly = true)
	public CursorPageResponse<NotificationDto> findAllNotConfirmed(
		UUID userId,
		String cursor,
		Instant after,
		int limit) {

		UUID cursorId = null;
		if (cursor != null && !cursor.isEmpty()) {
			cursorId = UUID.fromString(cursor);
		}

		List<Notification> notifications = notificationRepository.findUnconfirmedWithCursor(
			userId, cursorId, after, limit + 1);

		boolean hasNext = notifications.size() > limit;
		if (hasNext) {
			notifications = notifications.subList(0, limit);
		}

		List<NotificationDto> content = notifications.stream()
			.map(notificationMapper::toDto)
			.toList();

		String nextCursor = null;
		Instant nextAfter = null;
		if (hasNext && !notifications.isEmpty()) {
			Notification lastNotification = notifications.get(notifications.size() - 1);
			nextCursor = lastNotification.getId().toString();
			nextAfter = lastNotification.getCreatedAt();
		}

		Long totalElements = null;
		if (cursor == null) {
			totalElements = notificationRepository.countByUserIdAndConfirmedFalse(userId);
		}

		return new CursorPageResponse<>(
			content,
			nextCursor,
			nextAfter,
			content.size(),
			hasNext,
			totalElements);
	}

	@Transactional
	public void confirm(UUID notificationId, UUID userId) {
		Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
			.orElseThrow(() -> NotificationNotFoundException.withId(notificationId));

		log.debug("특정 알림 확인 처리 요청 시작: notificationId={}, userId={}", notificationId, userId);

		notification.confirm();

		log.info("특정 알림 확인 처리 요청 완료: notificationId={}, userId={}", notificationId, userId);
	}

	@Transactional
	public void confirmAll(UUID userId) {
		log.debug("전체 알림 확인 처리 요청 시작: userId={}", userId);

		int updatedCount = notificationRepository.confirmByUserId(userId);

		log.info("전체 알림 확인 처리 요청 완료: userId={}, count={}", userId, updatedCount);
	}

	@Transactional(readOnly = true)
	public long countUnconfirmed(UUID userId) {
		log.debug("확인되지 않은 알림 개수 조회 시작: userId={}", userId);

		long count = notificationRepository.countByUserIdAndConfirmedFalse(userId);

		log.info("확인되지 않은 알림 개수 조회 완료: userId={}", userId);
		return count;
	}
}
