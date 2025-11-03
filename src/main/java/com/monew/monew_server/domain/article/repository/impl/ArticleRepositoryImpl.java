package com.monew.monew_server.domain.article.repository.impl;

import static com.monew.monew_server.domain.article.entity.QArticle.*;
import static com.monew.monew_server.domain.article.entity.QArticleView.*;
import static com.monew.monew_server.domain.comment.entity.QComment.*;
import static com.monew.monew_server.domain.interest.entity.QArticleInterest.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.monew.monew_server.domain.article.dto.ArticleRequest;
import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.article.entity.ArticleSortType;
import com.monew.monew_server.domain.article.entity.ArticleSource;
import com.monew.monew_server.domain.article.repository.ArticleRepositoryCustom;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ArticleRepositoryImpl implements ArticleRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Article> findArticlesWithFilterAndCursor(ArticleRequest request, int size) {
		ArticleSortType orderBy = parseSortType(request.orderBy());
		String direction = Optional.ofNullable(request.direction()).orElse("DESC").toUpperCase();

		BooleanExpression cursorCondition = whereCursor(request, orderBy, direction);
		BooleanBuilder commonCondition = whereCondition(request);

		JPQLQuery<Article> query = queryFactory.selectFrom(article)
			.leftJoin(articleView).on(articleView.article.eq(article))
			.where(cursorCondition, commonCondition)
			.groupBy(article.id);

		OrderSpecifier<?> primaryOrder;
		OrderSpecifier<?> tieBreaker;

		if (orderBy == ArticleSortType.VIEW_COUNT) {
			NumberExpression<Long> countExpr = getCountExpression(ArticleSortType.VIEW_COUNT);
			primaryOrder = direction.equals("ASC") ? countExpr.asc() : countExpr.desc();
			tieBreaker = article.publishDate.desc();
		} else if (orderBy == ArticleSortType.COMMENT_COUNT) {
			NumberExpression<Long> countExpr = getCountExpression(ArticleSortType.COMMENT_COUNT);
			primaryOrder = direction.equals("ASC") ? countExpr.asc() : countExpr.desc();
			tieBreaker = article.publishDate.desc();
		} else {
			primaryOrder = direction.equals("ASC") ? article.publishDate.asc() : article.publishDate.desc();
			tieBreaker = direction.equals("ASC") ? article.id.asc() : article.id.desc();
		}

		query.orderBy(primaryOrder, tieBreaker);

		return query.limit(size).fetch();
	}

	@Override
	public long countArticlesWithFilter(ArticleRequest request) {
		BooleanBuilder condition = whereCondition(request);

		JPQLQuery<Long> query = queryFactory.select(article.id.countDistinct())
			.from(article)
			.where(condition);

		if (request.interestId() != null) {
			query.innerJoin(articleInterest).on(articleInterest.article.eq(article));
		}

		return query.fetchOne();
	}

	private BooleanBuilder whereCondition(ArticleRequest request) {
		BooleanBuilder builder = new BooleanBuilder();

		if (request.keyword() != null && !request.keyword().isBlank()) {
			builder.and(article.title.containsIgnoreCase(request.keyword())
				.or(article.summary.containsIgnoreCase(request.keyword())));
		}
		if (request.interestId() != null) {
			builder.and(articleInterest.interest.id.eq(request.interestId()));
		}
		if (request.sourceIn() != null && !request.sourceIn().isEmpty()) {
			List<ArticleSource> validSources = request.sourceIn().stream()
				.map(String::toUpperCase)
				.filter(ArticleSource::isValid)
				.map(ArticleSource::valueOf)
				.toList();
			builder.and(article.source.in(validSources));
		}

		if (request.publishDateFrom() != null && request.publishDateTo() != null) {
			Instant from = request.publishDateFrom().toInstant(ZoneOffset.UTC);
			Instant to = request.publishDateTo().toInstant(ZoneOffset.UTC);
			builder.and(article.publishDate.between(from, to));
		} else if (request.publishDateFrom() != null) {
			Instant from = request.publishDateFrom().toInstant(ZoneOffset.UTC);
			builder.and(article.publishDate.goe(from));
		} else if (request.publishDateTo() != null) {
			Instant to = request.publishDateTo().toInstant(ZoneOffset.UTC);
			builder.and(article.publishDate.loe(to));
		}

		builder.and(article.deletedAt.isNull());
		return builder;
	}

	private BooleanExpression whereCursor(ArticleRequest request, ArticleSortType orderBy, String direction) {
		if (request.cursor() == null || request.cursor().isBlank() || request.after() == null) {
			return null;
		}

		UUID cursorId = UUID.fromString(request.cursor());
		LocalDateTime after = request.after();
		Instant afterInstant = after.toInstant(ZoneOffset.UTC);

		BooleanExpression primarySort;
		BooleanExpression tieBreaker;

		if (orderBy == ArticleSortType.DATE) {
			if (direction.equals("ASC")) {
				primarySort = article.publishDate.gt(afterInstant);
				tieBreaker = article.publishDate.eq(afterInstant).and(article.id.gt(cursorId));
			} else {
				primarySort = article.publishDate.lt(afterInstant);
				tieBreaker = article.publishDate.eq(afterInstant).and(article.id.lt(cursorId));
			}
			return primarySort.or(tieBreaker);
		} else {
			Long afterValue = 0L;
			try {
				afterValue = Long.parseLong(after.toString());
			} catch (NumberFormatException ignored) {
			}

			NumberExpression<Long> countExpr = getCountExpression(orderBy);

			if (direction.equals("ASC")) {
				primarySort = countExpr.gt(afterValue);
				tieBreaker = countExpr.eq(afterValue).and(article.id.gt(cursorId));
			} else {
				primarySort = countExpr.lt(afterValue);
				tieBreaker = countExpr.eq(afterValue).and(article.id.lt(cursorId));
			}
			return primarySort.or(tieBreaker);
		}
	}

	private NumberExpression<Long> getCountExpression(ArticleSortType orderBy) {
		JPQLQuery<Long> countQuery;

		if (orderBy == ArticleSortType.COMMENT_COUNT) {
			countQuery = JPAExpressions.select(comment.id.count())
				.from(comment)
				.where(comment.article.id.eq(article.id).and(comment.deletedAt.isNull()));
		} else if (orderBy == ArticleSortType.VIEW_COUNT) {
			countQuery = JPAExpressions.select(articleView.id.count())
				.from(articleView)
				.where(articleView.article.id.eq(article.id));
		} else {
			return Expressions.numberTemplate(Long.class, "0");
		}

		return Expressions.numberTemplate(Long.class, "({0})", countQuery);
	}

	private ArticleSortType parseSortType(String orderBy) {
		if (orderBy == null)
			return ArticleSortType.DATE;
		try {
			return ArticleSortType.valueOf(orderBy.toUpperCase());
		} catch (IllegalArgumentException e) {
			return ArticleSortType.DATE;
		}
	}

	@Override
	public Optional<Article> findArticleById(UUID articleId) {
		return Optional.ofNullable(
			queryFactory.selectFrom(article)
				.where(article.id.eq(articleId)
					.and(article.deletedAt.isNull()))
				.fetchOne()
		);
	}

	@Override
	public Optional<Article> findByIdAndDeletedAtIsNull(UUID articleId) {
		return Optional.ofNullable(
			queryFactory.selectFrom(article)
				.where(article.id.eq(articleId)
					.and(article.deletedAt.isNull()))
				.fetchOne()
		);
	}

	@Override
	public List<Article> findBySourceInAndSourceUrlIn(
		List<ArticleSource> sources,
		List<String> sourceUrls) {

		if (sources.isEmpty() || sourceUrls.isEmpty()) {
			return List.of();
		}

		return queryFactory
			.selectFrom(article)
			.where(
				article.source.in(sources)
					.and(article.sourceUrl.in(sourceUrls))
					.and(article.deletedAt.isNull())
			)
			.fetch();
	}

}
