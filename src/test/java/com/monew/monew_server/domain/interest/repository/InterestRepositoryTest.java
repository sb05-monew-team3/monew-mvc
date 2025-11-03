package com.monew.monew_server.domain.interest.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.monew.monew_server.domain.interest.entity.Interest;
import com.monew.monew_server.exception.ErrorCode;
import com.monew.monew_server.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
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
class InterestRepositoryTest {

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

    @Autowired
    private InterestRepository interestRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("findSimilarInterests: 'Authentication' (길이 14) 기준으로 0.8 초과 항목만 필터링")
    void findSimilarInterests() {
        // given: DB에 다양한 유사도의 단어들을 저장
        interestRepository.save(Interest.builder().name("Authentication").build());
        interestRepository.save(Interest.builder().name("Authenticatian").build());
        interestRepository.save(Interest.builder().name("Authencicazion").build());
        interestRepository.save(Interest.builder().name("Authorization").build());
        interestRepository.save(Interest.builder().name("Microservice").build());
        interestRepository.flush();

        // when: "Authentication"으로 검색
        List<Interest> results = interestRepository.findSimilarInterests("Authentication");

        // then:
        // "Authentication" (Score 1.0),
        // "Authenticatian" (Score 0.928),
        // "Authencicazion" (Score 0.857) 3개가 반환되어야 한다.
        assertThat(results).hasSize(3);
        assertThat(results)
            .map(Interest::getName)
            .containsExactlyInAnyOrder("Authentication", "Authenticatian", "Authencicazion");
    }
    @Test
    @DisplayName("getOrThrow: ID가 존재할 때 Interest 객체를 반환한다 (Happy Path)")
    void getOrThrow_shouldReturnInterest_whenIdExists() {
        // given
        // 1. 테스트 데이터 저장
        Interest savedInterest = interestRepository.save(
            Interest.builder().name("Test Interest").build()
        );
        UUID existingId = savedInterest.getId();
        entityManager.flush(); // DB에 즉시 반영
        entityManager.clear(); // 영속성 컨텍스트 비우기

        // when
        // 2. 테스트 대상 메서드 호출
        Interest foundInterest = interestRepository.getOrThrow(existingId);

        // then
        // 3. 반환된 객체 검증
        assertThat(foundInterest).isNotNull();
        assertThat(foundInterest.getId()).isEqualTo(existingId);
        assertThat(foundInterest.getName()).isEqualTo("Test Interest");
    }

    @Test
    @DisplayName("getOrThrow: ID가 존재하지 않을 때 NotFoundException(INTEREST_NOT_FOUND)을 던진다")
    void getOrThrow_shouldThrowNotFoundException_whenIdDoesNotExist() {
        // given
        // 1. 존재하지 않는 ID 준비
        UUID nonExistentId = UUID.randomUUID();

        // when & then
        // 2. 예외가 발생하는지 검증
        NotFoundException exception = assertThrows(NotFoundException.class, () -> interestRepository.getOrThrow(nonExistentId));

        // 3. 예외의 상세 내용(ErrorCode, Message) 검증
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTEREST_NOT_FOUND);
        assertThat(exception.getMessage()).contains("Interest not found with id: " + nonExistentId);
    }
}
