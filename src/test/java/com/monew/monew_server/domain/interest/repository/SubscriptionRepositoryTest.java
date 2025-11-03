package com.monew.monew_server.domain.interest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.monew.monew_server.domain.interest.entity.Interest;
import com.monew.monew_server.domain.interest.entity.Subscription;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class SubscriptionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InterestRepository interestRepository;
    @Autowired private EntityManager entityManager;

    private User user1, user2;
    private Interest interest1, interest2;
    private Subscription sub1;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
        interestRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.builder()
            .email("test@test.com")
            .nickname("test")
            .password("test1234!")
            .build()
        );
        user2 = userRepository.save(User.builder()
            .email("test2@test.com")
            .nickname("test2")
            .password("test1234!")
            .build()
        );
        interest1 = interestRepository.save(Interest.builder().name("Interest 1").build());
        interest2 = interestRepository.save(Interest.builder().name("Interest 2").build());

        sub1 = subscriptionRepository.save(Subscription.builder().user(user1).interest(interest1).build());
        subscriptionRepository.save(Subscription.builder().user(user2).interest(interest1).build());

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findByUserIdAndInterestId: 구독 정보가 존재할 때 Optional<Subscription> 반환")
    void findByUserIdAndInterestId_shouldReturnSubscription_whenExists() {
        Optional<Subscription> found = subscriptionRepository
            .findByUserIdAndInterestId(user1.getId(), interest1.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(sub1.getId());
        assertThat(found.get().getUser().getId()).isEqualTo(user1.getId());
        assertThat(found.get().getInterest().getId()).isEqualTo(interest1.getId());
    }

    @Test
    @DisplayName("findByUserIdAndInterestId: 구독 정보가 존재하지 않을 때 (잘못된 유저) Optional.empty 반환")
    void findByUserIdAndInterestId_shouldReturnEmpty_whenUserNotSubscribed() {
        Optional<Subscription> found = subscriptionRepository
            .findByUserIdAndInterestId(user1.getId(), interest2.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdAndInterestId: 구독 정보가 존재하지 않을 때 (잘못된 관심사) Optional.empty 반환")
    void findByUserIdAndInterestId_shouldReturnEmpty_whenInterestNotSubscribed() {
        Optional<Subscription> found = subscriptionRepository
            .findByUserIdAndInterestId(user2.getId(), interest2.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("countByInterestId: 구독자가 2명일 때 2L 반환")
    void countByInterestId_shouldReturnCorrectCount_whenMultipleSubscriptions() {
        long count = subscriptionRepository.countByInterestId(interest1.getId());

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("countByInterestId: 구독자가 0명일 때 0L 반환")
    void countByInterestId_shouldReturnZero_whenNoSubscriptions() {
        long count = subscriptionRepository.countByInterestId(interest2.getId());

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("deleteByUserIdAndInterestId: 구독 정보가 존재할 때 해당 구독만 삭제")
    void deleteByUserIdAndInterestId_shouldDeleteSubscription_whenExists() {
        // given
        long initialCount = subscriptionRepository.count(); // 2
        UUID user1Id = user1.getId();
        UUID interest1Id = interest1.getId();

        // when: user1의 interest1 구독 취소
        subscriptionRepository.deleteByUserIdAndInterestId(user1Id, interest1Id);
        entityManager.flush(); // delete 쿼리 즉시 실행
        entityManager.clear();

        // then
        // 1. 전체 카운트가 1로 감소
        assertThat(subscriptionRepository.count()).isEqualTo(initialCount - 1);
        // 2. 삭제된 구독은 조회되지 않음
        assertThat(subscriptionRepository.findByUserIdAndInterestId(user1Id, interest1Id)).isEmpty();
        // 3. 다른 구독(user2, interest1)은 남아있음
        assertThat(subscriptionRepository.findByUserIdAndInterestId(user2.getId(), interest1Id)).isPresent();
    }

    @Test
    @DisplayName("deleteByUserIdAndInterestId: 구독 정보가 없을 때 아무 작업도 하지 않음 (멱등성)")
    void deleteByUserIdAndInterestId_shouldDoNothing_whenSubscriptionNotFound() {
        // given
        long initialCount = subscriptionRepository.count(); // 2
        UUID user1Id = user1.getId();
        UUID interest2Id = interest2.getId(); // 존재하지 않는 조합

        // when: user1의 interest2 구독 취소 (대상이 없음)
        subscriptionRepository.deleteByUserIdAndInterestId(user1Id, interest2Id);
        entityManager.flush();
        entityManager.clear();

        // then
        // 1. 전체 카운트가 변하지 않음
        assertThat(subscriptionRepository.count()).isEqualTo(initialCount);
        // 2. 기존 구독(user1, interest1)이 영향을 받지 않음
        assertThat(subscriptionRepository.findByUserIdAndInterestId(user1Id, interest1.getId())).isPresent();
    }
}
