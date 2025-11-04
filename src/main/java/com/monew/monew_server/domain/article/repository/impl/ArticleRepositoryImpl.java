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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ArticleRepositoryImpl implements ArticleRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Article> findArticlesWithFilterAndCursor(ArticleRequest request, int size) {
		ArticleSortType orderBy = parseSortType(request.orderBy());
		String direction = Optional.ofNullable(request.direction()).orElse("DESC").toUpperCase();

		log.info("=== Query Debug ===");
		log.info("Request orderBy: {}", request.orderBy());
		log.info("Parsed ArticleSortType: {}", orderBy);
		log.info("Direction: {}", direction);

		BooleanExpression cursorCondition = whereCursor(request, orderBy, direction);
		BooleanBuilder commonCondition = whereCondition(request);

		JPQLQuery<Article> query = queryFactory.selectFrom(article);

		if (request.interestId() != null) {
			query.innerJoin(articleInterest).on(articleInterest.article.eq(article));
		}

		query.where(cursorCondition, commonCondition);

		OrderSpecifier<?> primaryOrder;
		OrderSpecifier<?> secondaryOrder;

		if (orderBy == ArticleSortType.VIEW_COUNT) {
			NumberExpression<Long> countExpr = getCountExpression(ArticleSortType.VIEW_COUNT);
			primaryOrder = direction.equals("ASC") ? countExpr.asc() : countExpr.desc();

			secondaryOrder = direction.equals("ASC") ? article.publishDate.asc() : article.publishDate.desc();

			log.info("Using VIEW_COUNT ordering: primary={}, secondary=publishDate {}",
				direction, direction);
		} else if (orderBy == ArticleSortType.COMMENT_COUNT) {
			NumberExpression<Long> countExpr = getCountExpression(ArticleSortType.COMMENT_COUNT);
			primaryOrder = direction.equals("ASC") ? countExpr.asc() : countExpr.desc();
			secondaryOrder = direction.equals("ASC") ? article.publishDate.asc() : article.publishDate.desc();

			log.info("Using COMMENT_COUNT ordering: primary={}, secondary=publishDate {}",
				direction, direction);
		} else {
			primaryOrder = direction.equals("ASC") ? article.publishDate.asc() : article.publishDate.desc();
			secondaryOrder = direction.equals("ASC") ? article.id.asc() : article.id.desc();

			log.info("Using DATE ordering: primary=publishDate {}, secondary=id {}",
				direction, direction);
		}

		query.orderBy(primaryOrder, secondaryOrder);

		List<Article> results = query.limit(size).fetch();

		log.info("Query returned {} articles", results.size());
		if (!results.isEmpty()) {
			Article first = results.get(0);
			Article last = results.get(results.size() - 1);
			log.info("First article: id={}, publishDate={}", first.getId(), first.getPublishDate());
			log.info("Last article: id={}, publishDate={}", last.getId(), last.getPublishDate());
		}

		return results;
	}

	@Override
	public long countArticlesWithFilter(ArticleRequest request) {
		BooleanBuilder condition = whereCondition(request);

		JPQLQuery<Long> query = queryFactory.select(article.id.countDistinct())
			.from(article);

		if (request.interestId() != null) {
			query.innerJoin(articleInterest).on(articleInterest.article.eq(article));
		}

		query.where(condition);

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
				.filter(name -> {
					try {
						ArticleSource.valueOf(name);
						return true;
					} catch (IllegalArgumentException e) {
						return false;
					}
				})
				.map(ArticleSource::valueOf)
				.toList();

			if (!validSources.isEmpty()) {
				builder.and(article.source.in(validSources));
			}
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

		LocalDateTime after = request.after();
		Instant afterInstant = after.toInstant(ZoneOffset.UTC);

		BooleanExpression tieBreaker;
		if (direction.equals("ASC")) {
			tieBreaker = article.publishDate.gt(afterInstant);
		} else {
			tieBreaker = article.publishDate.lt(afterInstant);
		}

		if (orderBy == ArticleSortType.DATE) {

			Instant cursorInstant;
			try {
				cursorInstant = Instant.parse(request.cursor());
			} catch (Exception e) {
				log.warn("Invalid cursor value for DATE sort: {}, ignoring cursor", request.cursor());
				return null;
			}

			if (direction.equals("ASC")) {
				return article.publishDate.gt(cursorInstant)
					.or(article.publishDate.eq(cursorInstant).and(tieBreaker));
			} else {

				return article.publishDate.lt(cursorInstant)
					.or(article.publishDate.eq(cursorInstant).and(tieBreaker));
			}
		} else {
			long cursorCount;
			try {
				cursorCount = Long.parseLong(request.cursor());
			} catch (NumberFormatException e) {
				log.warn("Invalid cursor value for {} sort: {}, ignoring cursor", orderBy, request.cursor());
				return null;
			}

			NumberExpression<Long> countExpr = getCountExpression(orderBy);

			if (direction.equals("ASC")) {
				return countExpr.gt(cursorCount)
					.or(countExpr.eq(cursorCount).and(tieBreaker));
			} else {
				return countExpr.lt(cursorCount)
					.or(countExpr.eq(cursorCount).and(tieBreaker));
			}
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
		if (orderBy == null || orderBy.isBlank()) {
			return ArticleSortType.DATE;
		}

		String enumName = switch (orderBy.toLowerCase()) {
			case "viewcount" -> "VIEW_COUNT";
			case "commentcount" -> "COMMENT_COUNT";
			case "publishdate", "date" -> "DATE";
			default -> orderBy.toUpperCase();
		};

		try {
			return ArticleSortType.valueOf(enumName);
		} catch (IllegalArgumentException e) {
			log.warn("Invalid orderBy value: {}, using DATE as default", orderBy);
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