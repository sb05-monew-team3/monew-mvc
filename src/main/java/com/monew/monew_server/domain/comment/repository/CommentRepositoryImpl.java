package com.monew.monew_server.domain.comment.repository;

import com.monew.monew_server.domain.comment.entity.Comment;
import com.monew.monew_server.domain.comment.entity.QComment;
import com.monew.monew_server.domain.comment.entity.QCommentLike;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepositoryCustom {

    // SQL 쿼리를 자바 코드로 조립해줌(JPAQueryFactory)
    private final JPAQueryFactory queryFactory;


    // 메인 메서드 : 커서 기반으로 댓글 조회
    @Override
    public List<Comment> findByArticleIdWithCursor(
            UUID articleId, // 어떤 기사의 댓글?
            String orderBy, // 정렬 기준 (생성날짜 와 좋아요 수)
            String direction, // 오름차순 내림차순 결정
            String cursor, // 마지막으로 본댓글 ID
            Instant after, // 마지막으로 본 댓글 시간
            int limit // 몇 개 가져올지
    ) {
        QComment comment = QComment.comment;

        // 1. 기본 쿼리 조회시작: WHERE article_id = ?
        var query = queryFactory
                .selectFrom(comment) // 특정 컬럼만 선택
                .where(
                        comment.article.id.eq(articleId),  // 기사 ID로 필터링
                        comment.deletedAt.isNull(),         // 삭제되지 않은 댓글만
                        cursorCondition(comment, orderBy, direction, cursor, after)  // 커서 조건
                )
                .orderBy(getOrderSpecifier(comment, orderBy, direction))  // 동적 정렬 (요청 파라미터에 따라 정렬 조건 바꿔주는 것)
                .limit(limit + 1);  // hasNext 판단을 위해 1개 더 조회

        List<Comment> results = query.fetch();

        log.debug("조회된 댓글 수: {} (limit: {})", results.size(), limit);

        return results;
    }


    // 커서 분별 조건 만들기
    private BooleanExpression cursorCondition(
            QComment comment,
            String orderBy, // 정렬 기준
            String direction, // 방향 ASD/DESC
            String cursor, // 마지막으로 본 댓글
            Instant after // 마지막으로 본 댓글 시간
    ) {
        // 첫 페이지는 첫장을 보여주는것 첫장에 아무것도 없다면 null값 반환
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        UUID cursorId = UUID.fromString(cursor); // 문자열을 UUID 로 변환하는 코드


        // createdAt 기준으로 커서
        if ("createdAt".equalsIgnoreCase(orderBy)) { // orderby 체크 좋아요 순인지 생성 순인지.
            return cursorConditionForCreatedAt(comment, direction, cursorId, after);
        } else if ("likeCount".equalsIgnoreCase(orderBy)) {
            return cursorConditionForLikeCount(comment, direction, cursorId);
        }

        // 기본값: createdAt 기준
        return cursorConditionForCreatedAt(comment, direction, cursorId, after);
    }



    //
    private BooleanExpression cursorConditionForCreatedAt(
            QComment comment,
            String direction,
            UUID cursorId,
            Instant after
    ) {
        if (after == null) {
            return null;
        }

        if ("DESC".equalsIgnoreCase(direction)) {
            // 내림차순: 더 오래된 댓글
            return comment.createdAt.lt(after)
                    .or(comment.createdAt.eq(after).and(comment.id.lt(cursorId)));
        } else {
            // 오름차순: 더 최근 댓글
            return comment.createdAt.gt(after)
                    .or(comment.createdAt.eq(after).and(comment.id.gt(cursorId)));
        }
    }

    /**
     * likeCount 기준 커서 조건
     *
     * <주의>
     * likeCount는 Comment 엔티티에 직접 저장되지 않고,
     * CommentLike 테이블을 COUNT 해야 함
     * 현재는 간단하게 ID 기준으로만 처리
     * (추후 최적화 필요: likeCount를 Comment 테이블에 비정규화하거나 서브쿼리 사용)
     */
    private BooleanExpression cursorConditionForLikeCount(
            QComment comment,
            String direction,
            UUID cursorId
    ) {
        if ("DESC".equalsIgnoreCase(direction)) {
            return comment.id.lt(cursorId);
        } else {
            return comment.id.gt(cursorId);
        }
    }

    /**
     * 동적 정렬 조건 생성
     *
     * <OrderSpecifier란?>
     * QueryDSL에서 ORDER BY 절을 표현하는 객체
     * - Order.ASC / Order.DESC: 정렬 방향
     * - comment.createdAt: 정렬할 컬럼
     *
     * @param comment   QComment 엔티티
     * @param orderBy   정렬 기준
     * @param direction 정렬 방향
     * @return OrderSpecifier 배열
     */
    private OrderSpecifier<?>[] getOrderSpecifier(QComment comment, String orderBy, String direction) {
        Order order = "DESC".equalsIgnoreCase(direction) ? Order.DESC : Order.ASC;
        OrderSpecifier<?> idOrder = new OrderSpecifier<>(order, comment.id);

        if ("likeCount".equalsIgnoreCase(orderBy)) {
            QCommentLike subCommentLike = new QCommentLike("subCommentLike");

            OrderSpecifier<?> likeCountOrder = new OrderSpecifier<>(
                    order,
                    JPAExpressions
                            .select(subCommentLike.count())
                            .from(subCommentLike)
                            .where(subCommentLike.comment.eq(comment))
            );

            return new OrderSpecifier[]{likeCountOrder, idOrder};
        }

        return new OrderSpecifier[]{
                new OrderSpecifier<>(order, comment.createdAt),
                idOrder
        };
    }
}
