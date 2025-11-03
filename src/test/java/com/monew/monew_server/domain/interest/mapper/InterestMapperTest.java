package com.monew.monew_server.domain.interest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.monew.monew_server.domain.interest.dto.InterestDto;
import com.monew.monew_server.domain.interest.entity.Interest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {InterestMapperImpl.class})
class InterestMapperTest {

    @Autowired
    private InterestMapper interestMapper;

    @Test
    @DisplayName("toDto: 모든 필드가 정상적으로 DTO에 매핑되어야 한다 (Happy Path)")
    void toDto_shouldMapAllFieldsCorrectly() {
        Interest interest = Interest.builder().name("Spring").build();
        List<String> keywords = List.of("k1", "k2");
        Long subscriberCount = 10L;
        Boolean subscribedByMe = true;

        InterestDto dto = interestMapper.toDto(interest, keywords, subscriberCount, subscribedByMe);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(interest.getId());
        assertThat(dto.name()).isEqualTo("Spring");
        assertThat(dto.keywords()).isEqualTo(keywords);
        assertThat(dto.keywords()).isNotSameAs(keywords);
        assertThat(dto.subscriberCount()).isEqualTo(10L);
        assertThat(dto.subscribedByMe()).isTrue();
    }

    @Test
    @DisplayName("toDto: 모든 입력 파라미터가 null일 때 null을 반환해야 한다")
    void toDto_shouldReturnNull_whenAllInputsAreNull() {
        InterestDto dto = interestMapper.toDto(null, null, null, null);

        assertThat(dto).isNull();
    }

    @Test
    @DisplayName("toDto: Interest 객체만 null일 때, DTO의 id와 name은 null이어야 한다")
    void toDto_shouldHandleNullInterest() {
        List<String> keywords = List.of("k1");
        Long subscriberCount = 5L;
        Boolean subscribedByMe = false;

        InterestDto dto = interestMapper.toDto(null, keywords, subscriberCount, subscribedByMe);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isNull();
        assertThat(dto.name()).isNull();
        assertThat(dto.keywords()).isEqualTo(keywords);
        assertThat(dto.subscriberCount()).isEqualTo(5L);
        assertThat(dto.subscribedByMe()).isFalse();
    }

    @Test
    @DisplayName("toDto: keywords 리스트가 null일 때, DTO의 keywords는 null이어야 한다")
    void toDto_shouldHandleNullKeywords() {
        Interest interest = Interest.builder().name("Java").build();

        InterestDto dto = interestMapper.toDto(interest, null, 5L, true);

        assertThat(dto).isNotNull();
        assertThat(dto.name()).isEqualTo("Java");
        assertThat(dto.keywords()).isNull();
        assertThat(dto.subscriberCount()).isEqualTo(5L);
        assertThat(dto.subscribedByMe()).isTrue();
    }

    @Test
    @DisplayName("toDto: subscriberCount가 null일 때, DTO의 subscriberCount는 null이어야 한다")
    void toDto_shouldHandleNullSubscriberCount() {
        Interest interest = Interest.builder().name("Java").build();

        InterestDto dto = interestMapper.toDto(interest, List.of("k1"), null, true);

        assertThat(dto).isNotNull();
        assertThat(dto.name()).isEqualTo("Java");
        assertThat(dto.keywords()).containsExactly("k1");
        assertThat(dto.subscriberCount()).isNull();
        assertThat(dto.subscribedByMe()).isTrue();
    }

    @Test
    @DisplayName("toDto: subscribedByMe가 null일 때, DTO의 subscribedByMe는 null이어야 한다")
    void toDto_shouldHandleNullSubscribedByMe() {
        // given
        // 다른 파라미터는 non-null로 설정하여
        // "모든 파라미터 null" 분기를 피하게 함
        Interest interest = Interest.builder().name("Java").build();
        List<String> keywords = List.of("k1");
        Long subscriberCount = 5L;

        // when
        // subscribedByMe 파라미터에 null 전달
        InterestDto dto = interestMapper.toDto(interest, keywords, subscriberCount, null);

        // then
        assertThat(dto).isNotNull();
        assertThat(dto.name()).isEqualTo("Java");
        assertThat(dto.keywords()).containsExactly("k1");
        assertThat(dto.subscriberCount()).isEqualTo(5L);
        assertThat(dto.subscribedByMe()).isNull(); // subscribedByMe가 null인지 확인
    }
}
