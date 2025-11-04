package com.monew.monew_server.domain.interest.repository;

import static com.monew.monew_server.domain.interest.entity.QInterest.*;
import static com.monew.monew_server.domain.interest.entity.QInterestKeyword.*;
import static com.monew.monew_server.domain.interest.entity.QSubscription.*;
import static com.monew.monew_server.domain.interest.enums.InterestSortField.*;
import static org.springframework.util.StringUtils.*;

import com.monew.monew_server.domain.interest.dto.CursorPageResponseInterestDto;
import com.monew.monew_server.domain.interest.dto.InterestDto;
import com.monew.monew_server.domain.interest.dto.InterestQuery;
import com.monew.monew_server.domain.interest.enums.InterestSortField;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class InterestQueryRepositoryImpl implements InterestQueryRepository {

    private final JPAQueryFactory queryFactory;

    public record InterestTempDto(
        UUID id,
        String name,
        Long subscriberCount,
        Boolean subscribedByMe,
        Instant createdAt
    ) {}

    @Override
    public CursorPageResponseInterestDto findAll(InterestQuery query, UUID userId) {

        JPQLQuery<Long> subscriberCountSubQuery = JPAExpressions
            .select(subscription.count())
            .from(subscription)
            .where(subscription.interest.id.eq(interest.id));

        BooleanExpression subscribedByMeSubQuery = (userId == null)
            ? Expressions.asBoolean(false)
            : JPAExpressions
            .selectOne()
            .from(subscription)
            .where(
                subscription.interest.id.eq(interest.id),
                subscription.user.id.eq(userId)
            ).exists();

        List<InterestTempDto> tempRows = queryFactory
            .select(Projections.constructor(InterestTempDto.class,
                interest.id,
                interest.name,
                subscriberCountSubQuery,
                subscribedByMeSubQuery,
                interest.createdAt
            ))
            .from(interest)
            .where(
                buildKeywordFilter(query.keyword()),
                buildRangeFromCursor(query.sortField(), query.desc(), query.cursor(), query.after())
            )
            .orderBy(buildOrderSpecifiers(query.sortField(), query.desc()))
            .limit(query.limit() + 1L)
            .fetch();

        boolean hasNext = tempRows.size() > query.limit();
        List<InterestTempDto> paginatedRows = hasNext ? tempRows.subList(0, query.limit()) : tempRows;

        // 키워드 목록 Batch Fetch
        List<UUID> interestIds = paginatedRows.stream()
            .map(InterestTempDto::id)
            .toList();

        Map<UUID, List<String>> keywordsMap;
        if (interestIds.isEmpty()) {
            keywordsMap = Collections.emptyMap();
        } else {
            List<Map.Entry<UUID, String>> pairs = queryFactory
                .select(
                    interestKeyword.interest.id,
                    interestKeyword.name
                )
                .from(interestKeyword)
                .where(interestKeyword.interest.id.in(interestIds))
                .fetch()
                .stream()
                .map(tuple -> Map.entry(
                    Objects.requireNonNull(tuple.get(interestKeyword.interest.id)),
                    Objects.requireNonNull(tuple.get(interestKeyword.name)))
                )
                .toList();

            keywordsMap = pairs.stream()
                .collect(Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
        }

        // 최종 DTO 조합
        List<InterestDto> finalRows = paginatedRows.stream()
            .map(tempDto -> new InterestDto(
                tempDto.id(),
                tempDto.name(),
                keywordsMap.getOrDefault(tempDto.id(), Collections.emptyList()),
                tempDto.subscriberCount(),
                tempDto.subscribedByMe()
            ))
            .toList();

        // 전체 카운트 조회
        Long totalElements = queryFactory
            .select(interest.count())
            .from(interest)
            .where(buildKeywordFilter(query.keyword()))
            .fetchOne();

        if (!hasNext) {
            return new CursorPageResponseInterestDto(
                finalRows,
                null,
                null,
                query.limit(),
                totalElements,
                false
            );
        }

        InterestTempDto lastTempDto = paginatedRows.get(paginatedRows.size() - 1);

        String nextCursor = query.sortField() == SUBSCRIBER_COUNT
            ? String.valueOf(lastTempDto.subscriberCount())
            : lastTempDto.name();

        Instant nextAfter = lastTempDto.createdAt();

        return new CursorPageResponseInterestDto(
            finalRows,
            nextCursor,
            nextAfter,
            query.limit(),
            totalElements,
            true
        );
    }

    private BooleanExpression buildRangeFromCursor(
        InterestSortField sortField,
        boolean desc,
        String cursor,
        Instant after
    ) {
        if (after == null || !hasText(cursor)) {
            return null;
        }

        BooleanExpression tieBreaker = interest.createdAt.lt(after);

        if (sortField == SUBSCRIBER_COUNT) {
            long cursorLong;
            try {
                cursorLong = Long.parseLong(cursor);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid cursor: " + cursor);
            }

            JPQLQuery<Long> f = JPAExpressions
                .select(subscription.count())
                .from(subscription)
                .where(subscription.interest.id.eq(interest.id));

            return desc
                ? f.lt(cursorLong)
                .or(f.eq(cursorLong).and(tieBreaker))
                : f.gt(cursorLong)
                .or(f.eq(cursorLong).and(tieBreaker));
        }
        StringExpression f = interest.name.lower();
        String cursorLower = cursor.toLowerCase();
        return desc
            ? f.lt(cursorLower).or(f.eq(cursorLower).and(tieBreaker))
            : f.gt(cursorLower).or(f.eq(cursorLower).and(tieBreaker));
    }

    private OrderSpecifier<?>[] buildOrderSpecifiers(InterestSortField sortField, boolean desc) {
        Order sortDirection = desc ? Order.DESC : Order.ASC;
        OrderSpecifier<?> createdAtOrder = new OrderSpecifier<>(Order.DESC, interest.createdAt);

        OrderSpecifier<?> primaryOrder;

        if (sortField == SUBSCRIBER_COUNT) {
            JPQLQuery<Long> subscriberCountSubQuery = JPAExpressions
                .select(subscription.count())
                .from(subscription)
                .where(subscription.interest.id.eq(interest.id));
            primaryOrder = new OrderSpecifier<>(sortDirection, subscriberCountSubQuery);
        } else {
            primaryOrder = new OrderSpecifier<>(sortDirection, interest.name.lower());
        }

        return new OrderSpecifier<?>[]{primaryOrder, createdAtOrder};
    }

    private BooleanExpression buildKeywordFilter(String keyword) {
        if (!hasText(keyword)) {
            return null;
        }

        BooleanExpression nameMatches = interest.name.containsIgnoreCase(keyword);

        BooleanExpression keywordMatches = JPAExpressions
            .selectOne()
            .from(interestKeyword)
            .where(
                interestKeyword.interest.id.eq(interest.id),
                interestKeyword.name.containsIgnoreCase(keyword)
            ).exists();

        return nameMatches.or(keywordMatches);
    }
}
