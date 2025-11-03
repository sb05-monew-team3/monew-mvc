package com.monew.monew_server.domain.interest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import com.monew.monew_server.domain.interest.dto.CursorPageResponseInterestDto;
import com.monew.monew_server.domain.interest.dto.InterestDto;
import com.monew.monew_server.domain.interest.dto.InterestQuery;
import com.monew.monew_server.domain.interest.dto.InterestRegisterRequest;
import com.monew.monew_server.domain.interest.dto.InterestUpdateRequest;
import com.monew.monew_server.domain.interest.dto.SubscriptionDto;
import com.monew.monew_server.domain.interest.entity.Interest;
import com.monew.monew_server.domain.interest.entity.InterestKeyword;
import com.monew.monew_server.domain.interest.entity.Subscription;
import com.monew.monew_server.domain.interest.repository.InterestKeywordRepository;
import com.monew.monew_server.domain.interest.repository.InterestRepository;
import com.monew.monew_server.domain.interest.repository.SubscriptionRepository;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import com.monew.monew_server.exception.ErrorCode;
import com.monew.monew_server.exception.InterestException;
import com.monew.monew_server.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import java.util.List;
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
class InterestServiceTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
        .withInitScript("init_fuzzystrmatch.sql");

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private InterestService interestService;
    @Autowired private InterestRepository interestRepository;
    @Autowired private InterestKeywordRepository interestKeywordRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private User user1;
    private Interest interest1;

    @BeforeEach
    void setUp() {
        user1 = userRepository.save(User.builder()
            .email("test@test.com")
            .nickname("test")
            .password("test1234!")
            .build()
        );

        interest1 = interestRepository.save(Interest.builder().name("Java").build());

        interestKeywordRepository.save(InterestKeyword.builder()
            .name("JVM")
            .interest(interest1)
            .build());

        interestRepository.save(Interest.builder().name("Spring Boot").build());

        interestRepository.save(Interest.builder().name("Python").build());

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("create - 관심사 생성 성공 (유사한 이름 없음)")
    void create_Success_WhenNoSimilarNameExists() {
        // given
        // 'Python'이 'Java'와 유사하지 않음 (similarity < 0.8)
        // @BeforeEach에서 Python, Java 이미 생성됨

        InterestRegisterRequest request = new InterestRegisterRequest(
            "GoLang", // 새로운 이름
            List.of("Goroutine", "gRPC")
        );

        // when
        InterestDto resultDto = interestService.create(request);

        // then
        assertThat(resultDto).isNotNull();
        assertThat(resultDto.id()).isNotNull();
        assertThat(resultDto.name()).isEqualTo("GoLang");
        assertThat(resultDto.keywords()).containsExactlyInAnyOrder("Goroutine", "gRPC");
        assertThat(resultDto.subscriberCount()).isZero(); // 생성 시 구독자 0
        assertThat(resultDto.subscribedByMe()).isNull(); // 생성 시 구독여부 null

        // DB 검증
        assertThat(interestRepository.findById(resultDto.id())).isPresent();
        assertThat(interestKeywordRepository.findAll()).hasSize(3); // JVM(Java) + Goroutine + gRPC
    }

    @Test
    @DisplayName("create - 관심사 생성 실패 (유사한 이름 중복)")
    void create_Fail_WhenSimilarNameExists() {
        // given
        // @BeforeEach에서 'Spring Boot'가 이미 저장됨
        InterestRegisterRequest request = new InterestRegisterRequest(
            "SpringBoot", // 네이티브 쿼리가 'Spring Boot'와 유사하다고 판단해야 함
            List.of("Java", "Microservice")
        );

        // when & then
        InterestException exception = assertThrows(InterestException.class,
            () -> interestService.create(request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTEREST_NAME_DUPLICATION);
        assertThat(exception.getDetails()).containsEntry("name", "SpringBoot");

        // DB 검증 (롤백 확인)
        assertThat(interestRepository.findAll().stream().map(Interest::getName))
            .contains("Spring Boot")
            .doesNotContain("SpringBoot");
        assertThat(interestKeywordRepository.findAll().stream().map(InterestKeyword::getName))
            .doesNotContain("Java", "Microservice");
    }

    @Test
    @DisplayName("subscribe - 신규 구독 성공 (기존 구독 없음)")
    void subscribe_shouldCreateNewSubscription_whenNotExisting() {
        // given: user1, interest1 (@BeforeEach)

        // when
        SubscriptionDto resultDto = interestService.subscribe(interest1.getId(), user1.getId());
        entityManager.flush(); // DB 반영
        entityManager.clear();

        // then
        // 1. DTO 검증
        assertThat(resultDto).isNotNull();
        assertThat(resultDto.interestId()).isEqualTo(interest1.getId());
        assertThat(resultDto.interestName()).isEqualTo("Java");
        assertThat(resultDto.interestKeywords()).containsExactly("JVM");
        assertThat(resultDto.interestSubscriberCount()).isEqualTo(1L); // 1명 구독
        assertThat(resultDto.createdAt()).isNotNull();

        // 2. DB 검증
        Optional<Subscription> savedSub = subscriptionRepository
            .findByUserIdAndInterestId(user1.getId(), interest1.getId());
        assertThat(savedSub).isPresent();
    }

    @Test
    @DisplayName("subscribe - 멱등성 테스트 (이미 구독 중일 때)")
    void subscribe_shouldReturnExistingSubscription_whenAlreadySubscribed() {
        // given: 1차 구독 (미리 저장)
        Subscription existingSub = subscriptionRepository.save(Subscription.builder()
            .user(user1)
            .interest(interest1)
            .build());
        entityManager.flush();
        long initialCount = subscriptionRepository.count();

        // when: 동일한 사용자가 2차 구독 요청
        SubscriptionDto resultDto = interestService.subscribe(interest1.getId(), user1.getId());

        // then
        // 1. DTO 검증 (기존 구독 정보 반환)
        assertThat(resultDto).isNotNull();
        assertThat(resultDto.id()).isEqualTo(existingSub.getId());
        assertThat(resultDto.interestSubscriberCount()).isEqualTo(1L); // 카운트가 2가 아님

        // 2. DB 검증 (멱등성: 추가 저장되지 않음)
        assertThat(subscriptionRepository.count()).isEqualTo(initialCount); // 구독자 수 변동 없음
    }

    @Test
    @DisplayName("subscribe - 실패 (관심사가 존재하지 않음)")
    void subscribe_shouldThrowException_whenInterestNotFound() {
        // given
        UUID fakeInterestId = UUID.randomUUID();

        // when & then
        NotFoundException exception = assertThrows(NotFoundException.class,
            () -> interestService.subscribe(fakeInterestId, user1.getId())
        );

        assertThat(exception.getMessage()).contains("Interest not found");

        // DB 검증
        assertThat(subscriptionRepository.count()).isZero();
    }

    @Test
    @DisplayName("findAll - 로직 위임 및 실행 검증 (QueryDSL 통합 테스트)")
    void findAll_shouldDelegateToRepository() {
        // given
        // @BeforeEach: user1, interest1("Java")
        // 1. 2번째 구독자 생성
        User user2 = userRepository.save(User.builder()
            .email("test2@test.com")
            .nickname("test2")
            .password("test1234!")
            .build()
        );
        subscriptionRepository.saveAll(
            List.of(
                Subscription.builder().user(user1).interest(interest1).build(),
                Subscription.builder().user(user2).interest(interest1).build()
            )
        );
        entityManager.flush(); // Java(2), Spring Boot(0), Python(0)

        // 2. 쿼리: 구독자순(DESC) 1개 조회
        InterestQuery query = new InterestQuery(
            null, "subscriberCount", "DESC", null, null, 1, null, null
        );

        // when
        CursorPageResponseInterestDto response = interestService.findAll(query, user1.getId());

        // then
        // 1. 결과 검증
        assertThat(response).isNotNull();
        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).hasSize(1);

        // 2. 내용 검증 (Java가 구독자 2명으로 1등)
        InterestDto resultDto = response.content().get(0);
        assertThat(resultDto.name()).isEqualTo("Java");
        assertThat(resultDto.subscriberCount()).isEqualTo(2L);
        assertThat(resultDto.subscribedByMe()).isTrue(); // user1로 조회
    }

    @Test
    @DisplayName("unsubscribe - 성공 (200 OK): 구독 정보를 성공적으로 삭제한다")
    void unsubscribe_shouldDeleteSubscription_whenExists() {
        // given:
        // 1. user1이 interest1을 구독하는 데이터를 생성
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
            .user(user1)
            .interest(interest1)
            .build());
        entityManager.flush();
        entityManager.clear();

        // 2. 삭제 전 구독 상태 확인
        long initialCount = subscriptionRepository.count();
        assertThat(subscriptionRepository.findById(subscription.getId())).isPresent();

        // when:
        // 3. 구독 취소 서비스 호출
        interestService.unsubscribe(interest1.getId(), user1.getId());
        entityManager.flush(); // delete 쿼리 즉시 실행
        entityManager.clear();

        // then:
        // 4. 구독이 삭제되었는지 검증
        assertThat(subscriptionRepository.count()).isEqualTo(initialCount - 1);
        assertThat(subscriptionRepository.findById(subscription.getId())).isEmpty();
    }

    @Test
    @DisplayName("unsubscribe - 성공 (멱등성): 구독 정보가 없어도 예외 없이 성공한다")
    void unsubscribe_shouldCompleteSuccessfully_whenSubscriptionDoesNotExist() {
        // given:
        // 1. user1이 interest1을 구독하지 않은 상태
        long initialCount = subscriptionRepository.count();
        assertThat(subscriptionRepository.findByUserIdAndInterestId(user1.getId(), interest1.getId())).isEmpty();

        // when:
        // 2. 존재하지 않는 구독에 대해 구독 취소 요청
        // (getOrThrow(interestId)는 통과, deleteBy...는 0건 삭제)
        interestService.unsubscribe(interest1.getId(), user1.getId());
        entityManager.flush();
        entityManager.clear();

        // then:
        // 3. 예외가 발생하지 않고, DB 카운트도 그대로인지 검증
        assertThat(subscriptionRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("unsubscribe - 실패 (404 Not Found): 관심사(Interest)가 존재하지 않는다")
    void unsubscribe_shouldThrowException_whenInterestDoesNotExist() {
        // given:
        // 1. 존재하지 않는 관심사 ID
        UUID fakeInterestId = UUID.randomUUID();

        // when & then:
        // 2. getOrThrow(interestId)에서 예외가 발생하는지 검증
        // (ErrorCode.INTEREST_NOT_FOUND는 이전 'getOrThrow' 테스트에서 가정한 값 사용)
        NotFoundException exception = assertThrows(NotFoundException.class, () -> interestService.unsubscribe(fakeInterestId, user1.getId()));

        // 3. 예외 상세 내용 검증
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTEREST_NOT_FOUND);
        assertThat(exception.getMessage()).contains("Interest not found with id: " + fakeInterestId);
    }

    @Test
    @DisplayName("delete - 성공: ID가 존재할 때 관심사를 삭제한다")
    void delete_shouldRemoveInterest_whenExists() {
        // given
        // 1. 삭제할 테스트 데이터 생성
        Interest interestToDelete = interestRepository.save(
            Interest.builder().name("Interest to be deleted").build()
        );
        UUID interestId = interestToDelete.getId();
        entityManager.flush();
        entityManager.clear();

        // 2. DB에 저장을 확인
        assertThat(interestRepository.findById(interestId)).isPresent();
        long initialCount = interestRepository.count();

        // when
        // 3. 테스트 대상 메서드 호출
        interestService.delete(interestId);
        entityManager.flush(); // delete 쿼리 즉시 실행
        entityManager.clear();

        // then
        // 4. DB에서 삭제되었는지 검증
        assertThat(interestRepository.count()).isEqualTo(initialCount - 1);
        assertThat(interestRepository.findById(interestId)).isEmpty();
    }

    @Test
    @DisplayName("delete - 실패 (404 Not Found): ID가 존재하지 않을 때 NotFoundException 발생")
    void delete_shouldThrowNotFound_whenInterestDoesNotExist() {
        // given
        // 1. 존재하지 않는 ID
        UUID fakeInterestId = UUID.randomUUID();

        // when & then
        // 2. getOrThrow(interestId)에서 예외가 발생하는지 검증
        NotFoundException exception = assertThrows(NotFoundException.class, () -> interestService.delete(fakeInterestId));

        // 3. 예외 상세 내용 검증 (ErrorCode.INTEREST_NOT_FOUND는 getOrThrow에 정의됨)
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTEREST_NOT_FOUND);
        assertThat(exception.getMessage()).contains("Interest not found with id: " + fakeInterestId);
    }

    @Test
    @DisplayName("update - 성공: 기존 키워드를 삭제하고 새 키워드로 갱신한다")
    void update_shouldReplaceKeywords_whenInterestExists() {
        // given
        // (@BeforeEach에서 interest1("Java")에 "JVM" 키워드가 있음)
        UUID interestId = interest1.getId();
        long initialSubCount = subscriptionRepository.countByInterestId(interestId); // 1

        InterestUpdateRequest request = new InterestUpdateRequest(
            List.of("Spring", "JPA") // "JVM"을 "Spring", "JPA"로 교체
        );

        // when
        InterestDto resultDto = interestService.update(interestId, request);
        entityManager.flush();
        entityManager.clear();

        // then
        // 1. DTO 검증 (이름, 구독자 수는 불변)
        assertThat(resultDto.id()).isEqualTo(interestId);
        assertThat(resultDto.name()).isEqualTo("Java"); // 이름은 변경되지 않음
        assertThat(resultDto.keywords()).containsExactlyInAnyOrder("Spring", "JPA");
        assertThat(resultDto.subscriberCount()).isEqualTo(initialSubCount); // 구독자 수 불변
        assertThat(resultDto.subscribedByMe()).isNull(); // userId가 없으므로 null

        // 2. DB 검증 (기존 "JVM"이 삭제되었는지)
        List<String> keywordsInDb = interestKeywordRepository.findKeywordsByInterestId(interestId);
        assertThat(keywordsInDb)
            .containsExactlyInAnyOrder("Spring", "JPA")
            .doesNotContain("JVM");
    }

    @Test
    @DisplayName("update - 실패 (404 Not Found): 갱신할 관심사가 존재하지 않는다")
    void update_shouldThrowNotFound_whenInterestDoesNotExist() {
        // given
        UUID fakeInterestId = UUID.randomUUID();
        InterestUpdateRequest request = new InterestUpdateRequest(List.of("k1"));

        // when & then (getOrThrow 검증)
        NotFoundException exception = assertThrows(NotFoundException.class, () -> interestService.update(fakeInterestId, request));

        // 3. 예외 상세 내용 검증
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTEREST_NOT_FOUND);
        assertThat(exception.getMessage()).contains("Interest not found with id: " + fakeInterestId);
    }
}
