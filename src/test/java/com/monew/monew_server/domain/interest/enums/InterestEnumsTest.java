package com.monew.monew_server.domain.interest.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterestEnumsTest {

    @Test
    @DisplayName("from() 메서드: 'subscriberCount' 문자열을 SUBSCRIBER_COUNT enum으로 변환")
    void from_shouldReturnSubscriberCount_whenGivenSubscriberCountString() {
        // given
        String rawValue = "subscriberCount";

        // when
        InterestSortField result = InterestSortField.from(rawValue);

        // then
        assertThat(result).isEqualTo(InterestSortField.SUBSCRIBER_COUNT);
    }

    @Test
    @DisplayName("from() 메서드: 'name' 문자열을 NAME enum으로 변환 (기본값)")
    void from_shouldReturnName_whenGivenNameString() {
        // given
        String rawValue = "name";

        // when
        InterestSortField result = InterestSortField.from(rawValue);

        // then
        assertThat(result).isEqualTo(InterestSortField.NAME);
    }

    @Test
    @DisplayName("from() 메서드: 알 수 없는 문자열을 NAME enum으로 변환 (기본값)")
    void from_shouldReturnName_whenGivenUnknownString() {
        // given
        String rawValue = "anyOtherUnknownString";

        // when
        InterestSortField result = InterestSortField.from(rawValue);

        // then
        assertThat(result).isEqualTo(InterestSortField.NAME);
    }

    @Test
    @DisplayName("from() 메서드: null을 NAME enum으로 변환 (기본값)")
    @SuppressWarnings("ConstantConditions")
    void from_shouldReturnName_whenGivenNull() {
        // given
        String rawValue = null;

        // when
        InterestSortField result = InterestSortField.from(rawValue);

        // then
        assertThat(result).isEqualTo(InterestSortField.NAME);
    }
}
