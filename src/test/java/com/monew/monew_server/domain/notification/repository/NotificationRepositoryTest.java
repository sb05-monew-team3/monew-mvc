package com.monew.monew_server.domain.notification.repository;

import static org.assertj.core.api.Assertions.*;

import com.monew.monew_server.config.JpaConfig;
import com.monew.monew_server.config.QuerydslConfig;
import com.monew.monew_server.domain.notification.entity.Notification;
import com.monew.monew_server.domain.notification.entity.NotificationResourceType;
import com.monew.monew_server.domain.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({QuerydslConfig.class, JpaConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("NotificationRepository 테스트")
class NotificationRepositoryTest {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private TestEntityManager entityManager;

	private User testUser;
	private Notification notification1;
	private Notification notification2;
	private Notification notification3;

	@BeforeEach
	void setUp() {
		testUser = User.builder()
			.nickname("nickname")
			.password("password1234")
			.email("test@monew.com")
			.build();

		entityManager.persistAndFlush(testUser);

		notification1 = Notification.builder()
			.user(testUser)
			.content("Test notification 1")
			.resourceType(NotificationResourceType.interest)
			.resourceId(UUID.randomUUID())
			.confirmed(false)
			.build();

		notification2 = Notification.builder()
			.user(testUser)
			.content("Test notification 2")
			.resourceType(NotificationResourceType.comment)
			.resourceId(UUID.randomUUID())
			.confirmed(false)
			.build();

		notification3 = Notification.builder()
			.user(testUser)
			.content("Test notification 3")
			.resourceType(NotificationResourceType.interest)
			.resourceId(UUID.randomUUID())
			.confirmed(true)
			.build();
	}

	@Test
	@DisplayName("확인되지 않은 알림 조회 성공")
	void findUnconfirmedWithCursor() {
		// Given
		Instant after = Instant.now().plusSeconds(10);
		notificationRepository.saveAll(List.of(notification1, notification2, notification3));
		entityManager.flush();

		// When
		List<Notification> result = notificationRepository.findUnconfirmedWithCursor(
			testUser.getId(), null, after, 10);

		// Then
		assertThat(result).isNotNull();
		assertThat(result).hasSize(2);
		assertThat(result).allMatch(n -> !n.isConfirmed());
	}

	@Test
	@DisplayName("사용자 ID와 알림 ID로 알림 조회")
	void findByIdAndUserId() {
		// Given
		notificationRepository.save(notification1);
		entityManager.flush();

		// When
		Optional<Notification> result = notificationRepository.findByIdAndUserId(notification1.getId(),
			testUser.getId());

		// Then
		assertThat(result).isPresent();
		assertThat(result.get()).isEqualTo(notification1);
	}

	@Test
	@DisplayName("존재하지 않는 알림 조회 불가")
	void findByIdAndUserId_NotFound() {
		// Given
		UUID notificationId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		// When
		Optional<Notification> result = notificationRepository.findByIdAndUserId(notificationId, userId);

		// Then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("다른 사용자의 알림 조회 불가")
	void findByIdAndUserId_DifferentUser() {
		// Given
		notificationRepository.save(notification1);
		UUID differentUserId = UUID.randomUUID();
		entityManager.flush();

		// When
		Optional<Notification> result = notificationRepository.findByIdAndUserId(notification1.getId(),
			differentUserId);

		// Then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("사용자의 모든 확인되지 않은 알림 일괄 확인 처리")
	void confirmByUserId() {
		// Given
		Instant after = Instant.now().plusSeconds(10);
		notificationRepository.saveAll(List.of(notification1, notification2, notification3));
		entityManager.flush();

		// When
		int updatedCount = notificationRepository.confirmByUserId(testUser.getId());

		// Then
		assertThat(updatedCount).isEqualTo(2);

		List<Notification> remainingUnconfirmed = notificationRepository.findUnconfirmedWithCursor(
			testUser.getId(), null, after, 100);
		assertThat(remainingUnconfirmed).isEmpty();
	}

	@Test
	@DisplayName("확인되지 않은 알림 개수 조회")
	void countByUserIdAndConfirmedFalse() {
		// Given
		notificationRepository.saveAll(List.of(notification1, notification2, notification3));
		entityManager.flush();

		// When
		long count = notificationRepository.countByUserIdAndConfirmedFalse(testUser.getId());

		// Then
		assertThat(count).isEqualTo(2);
	}

	@Test
	@DisplayName("다른 사용자의 알림 카운트 불가")
	void countByUserIdAndConfirmedFalse_DifferentUser() {
		// Given
		notificationRepository.saveAll(List.of(notification1, notification2, notification3));
		entityManager.flush();
		UUID differentUserId = UUID.randomUUID();

		// When
		long count = notificationRepository.countByUserIdAndConfirmedFalse(differentUserId);

		// Then
		assertThat(count).isZero();
	}

	@Test
	@DisplayName("확인되지 않은 알림이 없는 경우 0 반환")
	void countByUserIdAndConfirmedFalse_Zero() {
		// Given
		UUID userId = UUID.randomUUID();

		// When
		long count = notificationRepository.countByUserIdAndConfirmedFalse(userId);

		// Then
		assertThat(count).isZero();
	}
}