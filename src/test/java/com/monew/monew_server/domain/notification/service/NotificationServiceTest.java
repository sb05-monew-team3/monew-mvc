package com.monew.monew_server.domain.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.article.entity.ArticleSource;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 테스트")
class NotificationServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private NotificationMapper notificationMapper;

	@InjectMocks
	private NotificationService notificationService;

	private UUID userId;
	private User user;
	private UUID notificationId;
	private Notification notification;

    @BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		notificationId = UUID.randomUUID();

		user = User.builder()
			.id(userId)
			.build();

		notification = Notification.builder()
			.id(notificationId)
			.confirmed(false)
			.user(user)
			.content("Test notification")
			.resourceType(NotificationResourceType.interest)
			.resourceId(UUID.randomUUID())
			.build();

        new NotificationDto(
            notificationId,
            false,
            userId,
            "Test notification",
            NotificationResourceType.interest,
            UUID.randomUUID(),
            Instant.now(),
            Instant.now()
        );
	}

	@Test
	@DisplayName("알림(댓글 좋아요) 생성 성공")
	void createCommentLikeNotification() {
		// Given
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.title("title")
			.summary("summary")
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/")
			.createdAt(Instant.now())
			.publishDate(Instant.now())
			.build();

		User likedByUser = User.builder()
			.id(UUID.randomUUID())
			.nickname("좋아요누른사람")
			.build();

		Comment comment = Comment.builder()
			.id(UUID.randomUUID())
			.article(article)
			.user(user)
			.content("댓글")
			.build();

		given(userRepository.getReferenceById(likedByUser.getId())).willReturn(likedByUser);

		given(notificationRepository.save(any(Notification.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		// When
		notificationService.createCommentLikeNotification(comment, likedByUser.getId());

		// Then
		verify(userRepository).getReferenceById(likedByUser.getId());
		verify(notificationRepository).save(argThat(notification ->
			notification.getUser().getId().equals(userId) &&
				notification.getResourceType() == NotificationResourceType.comment &&
				notification.getResourceId().equals(comment.getId()) &&
				!notification.isConfirmed() &&
				notification.getContent().contains(likedByUser.getNickname())
		));
	}

	@Test
	@DisplayName("알림(댓글 좋아요) 셀프 - 알림 생성 안 됨")
	void createCommentLikeNotificationSelf() {
		// Given
		Comment comment = Comment.builder()
			.id(UUID.randomUUID())
			.user(user)
			.content("댓글")
			.build();

		// when
		notificationService.createCommentLikeNotification(comment, userId);

		// then
		verify(userRepository, never()).getReferenceById(any());
		verify(notificationRepository, never()).save(any());
	}

	@Test
	@DisplayName("확인되지 않은 알림 목록 조회 성공")
	void findAllNotConfirmed() {
		// Given
		int limit = 10;
		List<Notification> notifications = List.of(notification);

		given(notificationRepository.findUnconfirmedWithCursor(eq(userId), isNull(), isNull(), eq(limit + 1)))
			.willReturn(notifications);
		given(notificationRepository.countByUserIdAndConfirmedFalse(userId)).willReturn(1L);

		// When
		CursorPageResponse<NotificationDto> result = notificationService.findAllNotConfirmed(userId, null, null, limit);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.content()).hasSize(1);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.totalElements()).isEqualTo(1L);
		verify(notificationRepository).findUnconfirmedWithCursor(userId, null, null, limit + 1);
		verify(notificationRepository).countByUserIdAndConfirmedFalse(userId);
	}

	@Test
	@DisplayName("특정 알림을 확인 성공")
	void confirm() {
		// Given
		given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(notification));

		// When
		notificationService.confirm(notificationId, userId);

		// Then
		assertThat(notification.isConfirmed()).isTrue();
		verify(notificationRepository).findByIdAndUserId(notificationId, userId);
	}

	@Test
	@DisplayName("존재하지 않는 알림 확인 실패")
	void confirm_NotFound() {
		// Given
		given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> notificationService.confirm(notificationId, userId))
			.isInstanceOf(NotificationNotFoundException.class)
			.hasMessage("알림을 찾을 수 없습니다.");
		verify(notificationRepository).findByIdAndUserId(notificationId, userId);
	}

	@Test
	@DisplayName("모든 확인되지 않은 알림 일괄 확인 성공")
	void confirmAll() {
		// Given
		given(notificationRepository.confirmByUserId(userId)).willReturn(5);

		// When
		notificationService.confirmAll(userId);

		// Then
		verify(notificationRepository).confirmByUserId(userId);
	}

	@Test
	@DisplayName("0개 일괄 확인 성공")
	void confirm_AllEmpty() {
		// Given
		given(notificationRepository.confirmByUserId(userId)).willReturn(0);

		// When
		notificationService.confirmAll(userId);

		// Then
		verify(notificationRepository).confirmByUserId(userId);
	}

	@Test
	@DisplayName("확인되지 않은 알림 개수 조회 성공")
	void countUnconfirmed() {
		// Given
		given(notificationRepository.countByUserIdAndConfirmedFalse(userId)).willReturn(3L);

		// When
		long count = notificationService.countUnconfirmed(userId);

		// Then
		assertThat(count).isEqualTo(3L);
		verify(notificationRepository).countByUserIdAndConfirmedFalse(userId);
	}

	@Test
	@DisplayName("확인되지 않은 알림 없는 경우 0 반환")
	void countUnconfirmed_Zero() {
		// Given
		given(notificationRepository.countByUserIdAndConfirmedFalse(userId)).willReturn(0L);

		// When
		long count = notificationService.countUnconfirmed(userId);

		// Then
		assertThat(count).isZero();
		verify(notificationRepository).countByUserIdAndConfirmedFalse(userId);
	}
}