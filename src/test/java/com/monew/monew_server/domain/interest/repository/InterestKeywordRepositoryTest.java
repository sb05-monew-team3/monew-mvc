package com.monew.monew_server.domain.interest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.monew.monew_server.domain.interest.entity.Interest;
import com.monew.monew_server.domain.interest.entity.InterestKeyword;
import jakarta.persistence.EntityManager;
import java.util.List;
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
class InterestKeywordRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private InterestKeywordRepository interestKeywordRepository;
    @Autowired private InterestRepository interestRepository;
    @Autowired private EntityManager entityManager;

    private Interest interest1, interest2, interest3;

    @BeforeEach
    void setUp() {
        interestKeywordRepository.deleteAll();
        interestRepository.deleteAll();

        interest1 = interestRepository.save(Interest.builder().name("Java").build());
        interest2 = interestRepository.save(Interest.builder().name("Spring").build());
        interest3 = interestRepository.save(Interest.builder().name("Python").build());

        interestKeywordRepository.save(InterestKeyword.builder().name("JVM").interest(interest1).build());
        interestKeywordRepository.save(InterestKeyword.builder().name("OOP").interest(interest1).build());

        interestKeywordRepository.save(InterestKeyword.builder().name("Boot").interest(interest2).build());

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findKeywordsByInterestId: 여러 개의 키워드가 존재할 때 List<String> 반환")
    void findKeywordsByInterestId_shouldReturnKeywordNames_whenMultipleKeywordsExist() {
        List<String> keywords = interestKeywordRepository.findKeywordsByInterestId(interest1.getId());

        assertThat(keywords)
            .isNotNull()
            .hasSize(2)
            .containsExactlyInAnyOrder("JVM", "OOP");
    }

    @Test
    @DisplayName("findKeywordsByInterestId: 한 개의 키워드가 존재할 때 List<String> 반환")
    void findKeywordsByInterestId_shouldReturnKeywordName_whenOneKeywordExists() {
        List<String> keywords = interestKeywordRepository.findKeywordsByInterestId(interest2.getId());

        assertThat(keywords)
            .isNotNull()
            .hasSize(1)
            .containsExactly("Boot");
    }

    @Test
    @DisplayName("findKeywordsByInterestId: 키워드가 존재하지 않을 때 빈 리스트(empty list) 반환")
    void findKeywordsByInterestId_shouldReturnEmptyList_whenNoKeywordsExist() {
        List<String> keywords = interestKeywordRepository.findKeywordsByInterestId(interest3.getId());

        assertThat(keywords)
            .isNotNull()
            .isEmpty();
    }

    @Test
    @DisplayName("deleteAllByInterestId: 특정 관심사의 키워드를 일괄 삭제")
    void deleteAllByInterestId_shouldDeleteOnlySpecifiedKeywords() {
        // given
        // (@BeforeEach에서 interest1(Java)에 "JVM", "OOP" / interest2(Spring)에 "Boot"가 저장됨)
        long initialTotalCount = interestKeywordRepository.count(); // 3
        UUID interest1Id = interest1.getId();
        UUID interest2Id = interest2.getId();

        // when
        // interest1(Java)의 키워드만 삭제
        interestKeywordRepository.deleteAllByInterestId(interest1Id);
        entityManager.flush(); // delete 쿼리 즉시 실행
        entityManager.clear();

        // then
        // 1. 전체 카운트가 1로 감소해야 함
        assertThat(interestKeywordRepository.count()).isEqualTo(initialTotalCount - 2);

        // 2. interest1의 키워드는 조회되지 않아야 함
        assertThat(interestKeywordRepository.findKeywordsByInterestId(interest1Id)).isEmpty();

        // 3. interest2의 키워드는 남아있어야 함
        assertThat(interestKeywordRepository.findKeywordsByInterestId(interest2Id))
            .containsExactly("Boot");
    }

    @Test
    @DisplayName("deleteAllByInterestId: 키워드가 없는 관심사에 대해 실행해도 오류 없음 (멱등성)")
    void deleteAllByInterestId_shouldDoNothing_whenNoKeywordsExist() {
        // given
        // (@BeforeEach에서 interest3(Python)은 키워드가 없음)
        long initialTotalCount = interestKeywordRepository.count(); // 3
        UUID interest3Id = interest3.getId();

        // when
        // interest3(Python)의 키워드 삭제 (대상이 없음)
        interestKeywordRepository.deleteAllByInterestId(interest3Id);
        entityManager.flush();
        entityManager.clear();

        // then
        // 1. 예외가 발생하지 않아야 함
        // 2. 전체 카운트가 3으로 동일해야 함
        assertThat(interestKeywordRepository.count()).isEqualTo(initialTotalCount);
    }
}