package com.monew.monew_server.domain.interest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.monew.monew_server.domain.interest.dto.InterestDto;
import com.monew.monew_server.domain.interest.entity.Interest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterestMapperTest {

    private InterestMapper interestMapper;

    @BeforeEach
    void setUp() {
        interestMapper = new InterestMapperImpl();
    }

    @Test
    @DisplayName("모든 파라미터가 null일 경우 null을 반환해야 함")
    void testToDto_WhenAllInputsAreNull() {
        // given
        Interest interest = null;
        List<String> keywords = null;
        Long subscriberCount = null;
        Boolean subscribedByMe = null;

        // when
        InterestDto dto = interestMapper.toDto(interest, keywords, subscriberCount, subscribedByMe);

        // then
        // Branch 1 (True): if ( interest == null && keywords == null && ... )
        assertThat(dto).isNull();
    }

    @Test
    @DisplayName("모든 파라미터가 제공될 경우 DTO로 정상 매핑되어야 함 (Happy Path)")
    void testToDto_WhenAllInputsAreProvided() {
        // given
        UUID interestId = UUID.randomUUID();
        Interest interest = Interest.builder().id(interestId).name("Spring Boot").build();
        List<String> keywords = List.of("java", "backend");
        Long subscriberCount = 100L;
        Boolean subscribedByMe = true;

        // when
        InterestDto dto = interestMapper.toDto(interest, keywords, subscriberCount, subscribedByMe);

        // then
        // Branch 1 (False): 최소 하나가 null이 아님
        // Branch 2 (True): interest != null
        // Branch 3 (True): keywords != null
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(interestId);
        assertThat(dto.name()).isEqualTo("Spring Boot");
        assertThat(dto.keywords()).isEqualTo(keywords);
        assertThat(dto.subscriberCount()).isEqualTo(subscriberCount);
        assertThat(dto.subscribedByMe()).isEqualTo(subscribedByMe);

        // keywords 리스트가 원본과 다른 새 인스턴스인지 확인 (방어적 복사)
        assertThat(dto.keywords()).isNotSameAs(keywords);
    }

    @Test
    @DisplayName("Interest 객체만 null일 경우 ID와 Name이 null로 매핑되어야 함")
    void testToDto_WhenInterestIsNull() {
        // given
        Interest interest = null;
        List<String> keywords = List.of("react", "frontend");
        Long subscriberCount = 50L;
        Boolean subscribedByMe = false;

        // when
        InterestDto dto = interestMapper.toDto(interest, keywords, subscriberCount, subscribedByMe);

        // then
        // Branch 1 (False)
        // Branch 2 (False): interest == null
        // Branch 3 (True): keywords != null
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isNull();
        assertThat(dto.name()).isNull();
        assertThat(dto.keywords()).isEqualTo(keywords);
        assertThat(dto.subscriberCount()).isEqualTo(subscriberCount);
        assertThat(dto.subscribedByMe()).isEqualTo(subscribedByMe);
    }

    @Test
    @DisplayName("Keywords 리스트만 null일 경우 Keywords가 null로 매핑되어야 함")
    void testToDto_WhenKeywordsAreNull() {
        // given
        UUID interestId = UUID.randomUUID();
        Interest interest = Interest.builder().id(interestId).name("Database").build();
        List<String> keywords = null;
        Long subscriberCount = 200L;
        Boolean subscribedByMe = true;

        // when
        InterestDto dto = interestMapper.toDto(interest, keywords, subscriberCount, subscribedByMe);

        // then
        // Branch 1 (False)
        // Branch 2 (True): interest != null
        // Branch 3 (False): keywords == null
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(interestId);
        assertThat(dto.name()).isEqualTo("Database");
        assertThat(dto.keywords()).isNull();
        assertThat(dto.subscriberCount()).isEqualTo(subscriberCount);
        assertThat(dto.subscribedByMe()).isEqualTo(subscribedByMe);
    }

    @Test
    @DisplayName("Interest와 Keywords가 모두 null일 경우 해당 필드들이 null로 매핑되어야 함")
    void testToDto_WhenInterestAndKeywordsAreNull() {
        // given
        Interest interest = null;
        List<String> keywords = null;
        Long subscriberCount = 10L; // 이 값이 null이 아니므로 Branch 1은 False가 됨
        Boolean subscribedByMe = false;

        // when
        InterestDto dto = interestMapper.toDto(interest, keywords, subscriberCount, subscribedByMe);

        // then
        // Branch 1 (False)
        // Branch 2 (False): interest == null
        // Branch 3 (False): keywords == null
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isNull();
        assertThat(dto.name()).isNull();
        assertThat(dto.keywords()).isNull();
        assertThat(dto.subscriberCount()).isEqualTo(subscriberCount);
        assertThat(dto.subscribedByMe()).isEqualTo(subscribedByMe);
    }
}
