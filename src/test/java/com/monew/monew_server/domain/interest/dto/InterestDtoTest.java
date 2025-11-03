package com.monew.monew_server.domain.interest.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.monew.monew_server.domain.interest.enums.InterestSortField;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterestDtoTest {

    @Test
    @DisplayName("InterestQuery: 생성자 - null 입력 시 기본값 적용")
    void interestQuery_shouldApplyDefaults_whenNullsAreProvided() {
        // given
        InterestQuery query = new InterestQuery(null, null, null, null, null, null, null, null);

        // then
        assertThat(query.orderBy()).isEqualTo(InterestQuery.DEFAULT_ORDER_BY); // "name"
        assertThat(query.direction()).isEqualTo(InterestQuery.DEFAULT_DIRECTION); // "DESC"
        assertThat(query.limit()).isEqualTo(InterestQuery.DEFAULT_LIMIT); // 6
    }

    @Test
    @DisplayName("InterestQuery: 생성자 - 커스텀 값 (ASC) 적용")
    void interestQuery_shouldUseCustomValues_whenProvided() {
        // given
        Instant now = Instant.now();
        InterestQuery query = new InterestQuery(
            "keyword", "subscriberCount", "ASC", "cursor", now, 10, null, null
        );

        // then
        assertThat(query.keyword()).isEqualTo("keyword");
        assertThat(query.orderBy()).isEqualTo("subscriberCount");
        assertThat(query.direction()).isEqualTo("ASC");
        assertThat(query.cursor()).isEqualTo("cursor");
        assertThat(query.after()).isEqualTo(now);
        assertThat(query.limit()).isEqualTo(10);
    }

    @Test
    @DisplayName("InterestQuery: 생성자 - @JsonIgnore 필드(sortField, desc) 계산")
    void interestQuery_shouldCalculateIgnoredFields() {
        // given 1: DESC (기본값)
        InterestQuery queryDesc = new InterestQuery(null, null, null, null, null, null, null, null);
        // then 1:
        assertThat(queryDesc.sortField()).isEqualTo(InterestSortField.NAME); // "name" -> NAME
        assertThat(queryDesc.desc()).isTrue(); // "DESC" -> true

        // given 2: ASC (커스텀)
        InterestQuery queryAsc = new InterestQuery(null, "subscriberCount", "asc", null, null, null, null, null);
        // then 2:
        assertThat(queryAsc.sortField()).isEqualTo(InterestSortField.SUBSCRIBER_COUNT); // "subscriberCount" -> SUBSCRIBER_COUNT
        assertThat(queryAsc.desc()).isFalse(); // "asc" -> false
    }

    @Test
    @DisplayName("InterestQuery: 생성자 - limit이 1보다 작으면 IllegalArgumentException 발생")
    void interestQuery_shouldThrowException_whenLimitIsLessThanOne() {
        // when & then: limit = 0
        assertThatThrownBy(() -> {
            new InterestQuery(null, null, null, null, null, 0, null, null);
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be greater than 0");

        // when & then: limit = -1
        assertThatThrownBy(() -> {
            new InterestQuery(null, null, null, null, null, -1, null, null);
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be greater than 0");
    }

    @Test
    @DisplayName("InterestSortField: from() 메서드 로직 커버")
    void interestSortField_from_logic() {
        // "subscriberCount" 문자열을 올바르게 변환하는지
        assertThat(InterestSortField.from("subscriberCount")).isEqualTo(InterestSortField.SUBSCRIBER_COUNT);

        // 그 외 모든 문자열을 기본값(NAME)으로 변환하는지
        assertThat(InterestSortField.from("name")).isEqualTo(InterestSortField.NAME);
        assertThat(InterestSortField.from("anyOtherString")).isEqualTo(InterestSortField.NAME);
    }

    @Test
    @DisplayName("데이터 DTOs: 생성자 및 Accessor 단순 호출 (커버리지용)")
    void dataDtos_constructorAndAccessor_coverage() {
        UUID id = UUID.randomUUID();
        UUID interestId = UUID.randomUUID();
        Instant now = Instant.now();
        List<String> keywords = List.of("k1", "k2");

        InterestDto interestDto = new InterestDto(id, "name", keywords, 10L, true);
        assertThat(interestDto.id()).isEqualTo(id);
        assertThat(interestDto.name()).isEqualTo("name");
        assertThat(interestDto.keywords()).isEqualTo(keywords);
        assertThat(interestDto.subscriberCount()).isEqualTo(10L);
        assertThat(interestDto.subscribedByMe()).isTrue();

        CursorPageResponseInterestDto pageDto = new CursorPageResponseInterestDto(
            List.of(interestDto), "cursor", now, 1, 100L, true
        );
        assertThat(pageDto.content()).contains(interestDto);
        assertThat(pageDto.nextCursor()).isEqualTo("cursor");
        assertThat(pageDto.nextAfter()).isEqualTo(now);
        assertThat(pageDto.size()).isEqualTo(1);
        assertThat(pageDto.totalElements()).isEqualTo(100L);
        assertThat(pageDto.hasNext()).isTrue();

        InterestRegisterRequest requestDto = new InterestRegisterRequest("request name", keywords);
        assertThat(requestDto.name()).isEqualTo("request name");
        assertThat(requestDto.keywords()).isEqualTo(keywords);

        SubscriptionDto subDto = new SubscriptionDto(
            id,
            interestId,
            "interest name",
            keywords,
            20L,
            now
        );
        assertThat(subDto.id()).isEqualTo(id);
        assertThat(subDto.interestId()).isEqualTo(interestId);
        assertThat(subDto.interestName()).isEqualTo("interest name");
        assertThat(subDto.interestKeywords()).isEqualTo(keywords);
        assertThat(subDto.interestSubscriberCount()).isEqualTo(20L);
        assertThat(subDto.createdAt()).isEqualTo(now);
    }
}
