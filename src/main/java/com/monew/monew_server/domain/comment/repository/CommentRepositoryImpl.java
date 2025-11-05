package com.monew.monew_server.domain.comment.repository;

import com.monew.monew_server.domain.comment.entity.Comment;
import com.monew.monew_server.domain.comment.entity.QComment;
import com.monew.monew_server.domain.comment.entity.QCommentLike;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Comment> findByArticleIdWithCursor(
            UUID articleId,
            String orderBy,
            String direction,
            String cursor,
            Instant after,
            int limit
    ) {
        QComment comment = QComment.comment;

        var query = queryFactory
                .selectFrom(comment)
                .where(
                        comment.article.id.eq(articleId),
                        comment.deletedAt.isNull(),
                        cursorCondition(comment, orderBy, direction, cursor, after)
                )
                .orderBy(getOrderSpecifier(comment, orderBy, direction))
                .limit(limit + 1L);

        List<Comment> results = query.fetch();

        log.debug("조회된 댓글 수: {} (limit: {})", results.size(), limit);

        return results;
    }


    private BooleanExpression cursorCondition(
            QComment comment,
            String orderBy,
            String direction,
            String cursor,
            Instant after
    ) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }

        if ("createdAt".equalsIgnoreCase(orderBy)) {
            Instant lastCreatedAt = Instant.parse(cursor);
            return cursorConditionForCreatedAt(comment, direction, lastCreatedAt, after);
        } else if ("likeCount".equalsIgnoreCase(orderBy)) {
            Long lastLikeCount = Long.parseLong(cursor);
            return cursorConditionForLikeCount(comment, direction, lastLikeCount, after);
        }

        Instant lastCreatedAt = Instant.parse(cursor);
        return cursorConditionForCreatedAt(comment, direction, lastCreatedAt, after);
    }



    private BooleanExpression cursorConditionForCreatedAt(
            QComment comment,
            String direction,
            Instant lastCreatedAt,
            Instant after
    ) {
        if (after == null) {
            return null;
        }

        if ("DESC".equalsIgnoreCase(direction)) {
            return comment.createdAt.lt(lastCreatedAt);
        } else {
            return comment.createdAt.gt(lastCreatedAt);
        }
    }

    private BooleanExpression cursorConditionForLikeCount(
            QComment comment,
            String direction,
            Long lastLikeCount,
            Instant after
    ) {
        QCommentLike subCommentLike = new QCommentLike("subCommentLike");

        var likeCountSubQuery = JPAExpressions
                .select(subCommentLike.count())
                .from(subCommentLike)
                .where(subCommentLike.comment.eq(comment));

        if ("DESC".equalsIgnoreCase(direction)) {
            return likeCountSubQuery.lt(lastLikeCount)
                    .or(likeCountSubQuery.eq(lastLikeCount).and(comment.createdAt.lt(after)));
        } else {
            return likeCountSubQuery.gt(lastLikeCount)
                    .or(likeCountSubQuery.eq(lastLikeCount).and(comment.createdAt.gt(after)));
        }
    }

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
