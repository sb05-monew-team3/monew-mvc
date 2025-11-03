package com.monew.monew_server.domain.interest.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.monew.monew_server.domain.interest.dto.CursorPageResponseInterestDto;
import com.monew.monew_server.domain.interest.dto.InterestQuery;
import com.monew.monew_server.domain.interest.entity.Interest;
import com.monew.monew_server.domain.interest.entity.InterestKeyword;
import com.monew.monew_server.domain.interest.entity.Subscription;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class InterestQueryRepositoryImplTest {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private InterestQueryRepositoryImpl interestQueryRepository;

    @Autowired private InterestRepository interestRepository;
    @Autowired private InterestKeywordRepository interestKeywordRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private User user1;
    private Interest iReact;
    private Interest iJava;
    private Interest iSpring;

    /**
     * 테스트 데이터 설정:
     * - Order (Name DESC): Spring -> React -> Java -> Docker
     * - Order (SubCount DESC): Java(2) -> Spring(1) -> Docker(0) -> React(0)
     * - Tie-Breaker (SubCount=0, createdAt DESC): Docker -> React (Docker가 더 최신)
     * - User1 구독: Java, Spring
     */
    @BeforeEach
    void setUp() throws InterruptedException {
        // 데이터 초기화 (외래키 제약조건 순서 고려)
        subscriptionRepository.deleteAll();
        interestKeywordRepository.deleteAll();
        interestRepository.deleteAll();
        userRepository.deleteAll();

        // 1. 유저 생성
        user1 = userRepository.save(User.builder()
            .email("test@test.com")
            .nickname("test")
            .password("test1234!")
            .build()
        );
        User user2 = userRepository.save(User.builder()
            .email("test2@test.com")
            .nickname("test2")
            .password("test1234!")
            .build()
        );

        // 2. 관심사 생성 (createdAt 순서 보장을 위해 sleep)
        iReact = interestRepository.save(Interest.builder().name("React").build());   // 0 subs
        Thread.sleep(10);
        iJava = interestRepository.save(Interest.builder().name("Java").build());    // 2 subs
        Thread.sleep(10);
        iSpring = interestRepository.save(Interest.builder().name("Spring").build());  // 1 sub
        Thread.sleep(10);
        interestRepository.save(Interest.builder().name("Docker").build());  // 0 subs (React보다 최신)

        // 3. 키워드 생성
        interestKeywordRepository.save(InterestKeyword.builder().name("Boot").interest(iSpring).build());
        interestKeywordRepository.save(InterestKeyword.builder().name("JVM").interest(iJava).build());
        interestKeywordRepository.save(InterestKeyword.builder().name("Web").interest(iReact).build());

        // 4. 구독 정보 생성
        subscriptionRepository.save(Subscription.builder().user(user1).interest(iJava).build());
        subscriptionRepository.save(Subscription.builder().user(user2).interest(iJava).build()); // Java: 2
        subscriptionRepository.save(Subscription.builder().user(user1).interest(iSpring).build()); // Spring: 1

        // 5. 영속성 컨텍스트 초기화 (N+1 및 서브쿼리 정확도)
        entityManager.flush();
        entityManager.clear();
    }

    // --- 1. 기본 조회 및 페이징 테스트 ---

    @Test
    @DisplayName("findAll: 1페이지 조회 (기본값: 이름 DESC, limit 2)")
    void findAll_Page1_DefaultSort_NameDesc() {
        // given
        InterestQuery query = new InterestQuery(null, null, null, null, null, 2, null, null);

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then
        assertThat(response.totalElements()).isEqualTo(4);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).hasSize(2);
        // 이름 DESC: Spring -> React -> Java -> Docker
        assertThat(response.content().get(0).name()).isEqualTo("Spring");
        assertThat(response.content().get(1).name()).isEqualTo("React");
    }

    @Test
    @DisplayName("findAll: 2페이지 조회 (이름 DESC, 커서 사용)")
    void findAll_Page2_NameDesc_WithCursor() {
        // given: 1페이지의 마지막(React) 정보를 커서로 사용
        InterestQuery query = new InterestQuery(
            null, "name", "DESC", "React", iReact.getCreatedAt(), 2, null, null
        );

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then: 마지막 페이지
        assertThat(response.totalElements()).isEqualTo(4);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.content()).hasSize(2);
        // 2페이지: Java -> Docker
        assertThat(response.content().get(0).name()).isEqualTo("Java");
        assertThat(response.content().get(1).name()).isEqualTo("Docker");
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextAfter()).isNull();
    }
    // --- 2. 정렬(ASC/DESC) 및 Tie-Breaker 테스트 ---

    @Test
    @DisplayName("findAll: 구독자순 DESC 1페이지 조회")
    void findAll_Page1_SubCountDesc() {
        // given
        InterestQuery query = new InterestQuery(
            null, "subscriberCount", "DESC", null, null, 2, null, null
        );

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then
        // SubCount DESC: Java(2) -> Spring(1) -> Docker(0) -> React(0)
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).name()).isEqualTo("Java"); // 2 subs
        assertThat(response.content().get(1).name()).isEqualTo("Spring"); // 1 sub
        assertThat(response.hasNext()).isTrue();

        // 커서 정보 (last element = Spring)
        assertThat(response.nextCursor()).isEqualTo("1"); // subscriberCount
        assertThat(response.nextAfter()).isEqualTo(iSpring.getCreatedAt());
    }

    @Test
    @DisplayName("findAll: 구독자순 DESC 2페이지 조회 (Tie-Breaker: createdAt DESC)")
    void findAll_Page2_SubCountDesc_WithTieBreaker() {
        // given: 1페이지 마지막(Spring) 커서
        InterestQuery query = new InterestQuery(
            null, "subscriberCount", "DESC", "1", iSpring.getCreatedAt(), 2, null, null
        );

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then
        // 2페이지: Docker(0) -> React(0) (Docker가 React보다 최신(createdAt DESC))
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).name()).isEqualTo("Docker");
        assertThat(response.content().get(1).name()).isEqualTo("React");
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("findAll: 이름 ASC 1페이지 조회")
    void findAll_Page1_NameAsc() {
        // given
        InterestQuery query = new InterestQuery(
            null, "name", "ASC", null, null, 2, null, null
        );

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then
        // 이름 ASC: Docker -> Java -> React -> Spring
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).name()).isEqualTo("Docker");
        assertThat(response.content().get(1).name()).isEqualTo("Java");
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("findAll: 2페이지 조회 (이름 ASC, 커서 사용)")
    void findAll_Page2_NameAsc_WithCursor() {
        // given: 이름 ASC 정렬: Docker -> Java -> React -> Spring
        // 1페이지(limit 2)는 Docker, Java.
        // 1페이지의 마지막(Java) 정보를 커서로 사용
        InterestQuery query = new InterestQuery(
            null, "name", "ASC", "Java", iJava.getCreatedAt(), 2, null, null
        );

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then: 마지막 페이지
        assertThat(response.totalElements()).isEqualTo(4);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.content()).hasSize(2);
        // 2페이지: React -> Spring
        assertThat(response.content().get(0).name()).isEqualTo("React");
        assertThat(response.content().get(1).name()).isEqualTo("Spring");
    }

    @Test
    @DisplayName("findAll: 2페이지 조회 (구독자순 ASC, 커서 사용)")
    void findAll_Page2_SubCountAsc_WithCursor() {
        // given: 구독자순 ASC: Docker(0, 최신) -> React(0, 이전) -> Spring(1) -> Java(2)
        // (0개짜리 Tie-breaker는 createdAt DESC이므로, createdAt이 느린(최신) Docker가 먼저 옴)
        // 1페이지(limit 2)는 React, Docker.
        // 1페이지의 마지막(Docker) 정보를 커서로 사용
        InterestQuery query = new InterestQuery(
            null, "subscriberCount", "ASC", "0", iReact.getCreatedAt(), 2, null, null
        );

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then: 마지막 페이지
        assertThat(response.totalElements()).isEqualTo(4);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.content()).hasSize(2);
        // 2페이지: Spring(1) -> Java(2)
        assertThat(response.content().get(0).name()).isEqualTo("Spring");
        assertThat(response.content().get(1).name()).isEqualTo("Java");
        assertThat(response.content().get(0).subscriberCount()).isEqualTo(1L);
        assertThat(response.content().get(1).subscriberCount()).isEqualTo(2L);
    }

    // --- 3. 필터링(buildKeywordFilter) 테스트 ---

    @Test
    @DisplayName("findAll: 키워드 필터링 (Interest.name 매칭)")
    void findAll_Filter_byInterestName() {
        // given
        InterestQuery query = new InterestQuery("Java", null, null, null, null, 10, null, null);

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).name()).isEqualTo("Java");
    }

    @Test
    @DisplayName("findAll: 키워드 필터링 (InterestKeyword.name 매칭)")
    void findAll_Filter_byKeywordName() {
        // given
        InterestQuery query = new InterestQuery("Boot", null, null, null, null, 10, null, null);

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then (Batch Fetch 로직 검증)
        assertThat(response.totalElements()).isEqualTo(1); // "Spring"
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).name()).isEqualTo("Spring");
        assertThat(response.content().get(0).keywords()).contains("Boot");
    }

    @Test
    @DisplayName("findAll: 키워드 필터링 (결과 없음)")
    void findAll_Filter_NoResults() {
        // given
        InterestQuery query = new InterestQuery("NonExistent", null, null, null, null, 10, null, null);

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then (keywordsMap.isEmpty() 분기 커버)
        assertThat(response.totalElements()).isZero();
        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    // --- 4. 엣지 케이스 및 분기 커버리지 ---

    @Test
    @DisplayName("findAll: 비로그인(userId=null) 시 'subscribedByMe'는 항상 false")
    void findAll_LoggedOut_SubscribedByMeIsFalse() {
        // given: 1페이지 (Spring, React), userId = null
        InterestQuery query = new InterestQuery(null, null, null, null, null, 2, null, null);

        // when
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, null);

        // then
        // user1은 Spring을 구독했지만, userId=null이므로 false여야 함
        assertThat(response.content().get(0).name()).isEqualTo("Spring");
        assertThat(response.content().get(0).subscribedByMe()).isFalse();
        // user1이 구독 안 한 React도 false
        assertThat(response.content().get(1).name()).isEqualTo("React");
        assertThat(response.content().get(1).subscribedByMe()).isFalse();
    }

    @Test
    @DisplayName("buildRangeFromCursor: 구독자순 정렬 시 잘못된 커서(숫자 아님) 예외")
    void buildRangeFromCursor_InvalidCursor_ThrowsException() {
        // given: orderBy=subscriberCount, cursor="not-a-number"
        InterestQuery query = new InterestQuery(
            null, "subscriberCount", "DESC", "not-a-number", Instant.now(), 10, null, null
        );

        // when & then (IllegalArgumentException 분기 커버)
        assertThatThrownBy(() -> interestQueryRepository.findAll(query, user1.getId()))
            .isInstanceOf(InvalidDataAccessApiUsageException.class)
            .hasMessageContaining("invalid cursor: not-a-number")
            .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("findAll: 'after'가 null이고 'cursor'만 있으면 1페이지로 동작")
    void findAll_WhenAfterIsNull_ShouldActAsPage1() {
        // given
        // 2페이지 커서("React")가 있지만, after(createdAt)가 null
        InterestQuery query = new InterestQuery(
            null, "name", "DESC", "React", null, 2, null, null
        );

        // when
        // buildRangeFromCursor가 (after == null) 조건 때문에 null을 반환해야 함
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then
        // 2페이지(Java, Docker)가 아닌 1페이지(Spring, React)가 나와야 함
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).name()).isEqualTo("Spring");
        assertThat(response.content().get(1).name()).isEqualTo("React");
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("findAll: 'cursor'가 null이고 'after'만 있으면 1페이지로 동작")
    void findAll_WhenCursorIsNull_ShouldActAsPage1() {
        // given
        // 2페이지 after(iReact.getCreatedAt())가 있지만, cursor가 null
        InterestQuery query = new InterestQuery(
            null, "name", "DESC", null, iReact.getCreatedAt(), 2, null, null
        );

        // when
        // buildRangeFromCursor가 (!hasText(cursor)) 조건 때문에 null을 반환해야 함
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then
        // 1페이지(Spring, React)가 나와야 함
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).name()).isEqualTo("Spring");
        assertThat(response.content().get(1).name()).isEqualTo("React");
    }

    @Test
    @DisplayName("findAll: 'cursor'가 빈 문자열이면 1페이지로 동작")
    void findAll_WhenCursorIsEmptyString_ShouldActAsPage1() {
        // given
        // 2페이지 after(iReact.getCreatedAt())가 있지만, cursor가 "" (empty string)
        InterestQuery query = new InterestQuery(
            null, "name", "DESC", "", iReact.getCreatedAt(), 2, null, null
        );

        // when
        // buildRangeFromCursor가 (!hasText(cursor)) 조건 때문에 null을 반환해야 함
        CursorPageResponseInterestDto response = interestQueryRepository.findAll(query, user1.getId());

        // then
        // 1페이지(Spring, React)가 나와야 함
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).name()).isEqualTo("Spring");
        assertThat(response.content().get(1).name()).isEqualTo("React");
    }
}
