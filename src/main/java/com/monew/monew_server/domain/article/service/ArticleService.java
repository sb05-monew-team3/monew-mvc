package com.monew.monew_server.domain.article.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monew.monew_server.domain.article.dto.ArticleRequest;
import com.monew.monew_server.domain.article.dto.ArticleResponse;
import com.monew.monew_server.domain.article.dto.ArticleRestoreResult;
import com.monew.monew_server.domain.article.dto.ArticleSaveDto;
import com.monew.monew_server.domain.article.dto.ArticleSourceDto;
import com.monew.monew_server.domain.article.dto.CursorPageResponseArticleDto;
import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.article.entity.ArticleSortType;
import com.monew.monew_server.domain.article.entity.ArticleSource;
import com.monew.monew_server.domain.article.entity.ArticleView;
import com.monew.monew_server.domain.article.mapper.ArticleMapper;
import com.monew.monew_server.domain.article.repository.ArticleRepository;
import com.monew.monew_server.domain.article.repository.ArticleRepositoryCustom;
import com.monew.monew_server.domain.article.repository.ArticleViewRepository;
import com.monew.monew_server.domain.article.storage.S3BinaryStorage;
import com.monew.monew_server.domain.comment.repository.CommentRepository;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.exception.ArticleNotFoundException;
import com.monew.monew_server.exception.BusinessException;
import com.monew.monew_server.exception.ErrorCode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ArticleService {

	private final ArticleRepository articleRepository; // JpaRepository
	private final ArticleRepositoryCustom articleRepositoryCustom; // @Qualifier 필요
	private final ArticleMapper articleMapper;
	private final ArticleViewRepository articleViewRepository;
	private final CommentRepository commentRepository;
	private final S3BinaryStorage s3BinaryStorage;
	private final ObjectMapper objectMapper;

	public ArticleService(
		ArticleRepository articleRepository,
		@Qualifier("articleRepositoryImpl") ArticleRepositoryCustom articleRepositoryCustom,
		ArticleMapper articleMapper,
		ArticleViewRepository articleViewRepository,
		CommentRepository commentRepository, S3BinaryStorage s3BinaryStorage
	) {
		this.articleRepository = articleRepository;
		this.articleRepositoryCustom = articleRepositoryCustom;
		this.articleMapper = articleMapper;
		this.articleViewRepository = articleViewRepository;
		this.commentRepository = commentRepository;
		this.s3BinaryStorage = s3BinaryStorage;
		this.objectMapper = new ObjectMapper();
	}

	private static final int DEFAULT_PAGE_SIZE = 10;
	@PersistenceContext
	private EntityManager entityManager;

	public CursorPageResponseArticleDto fetchArticles(ArticleRequest request, UUID currentUserId) {

		int requestedSize = request.limit() != null ? request.limit() : DEFAULT_PAGE_SIZE;
		int fetchSize = requestedSize + 1;

		List<Article> fetchedArticles = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, fetchSize);
		long totalElements = articleRepositoryCustom.countArticlesWithFilter(request);
		boolean hasNext = fetchedArticles.size() > requestedSize;

		List<ArticleResponse> allResponses = articleMapper.toResponseList(fetchedArticles);
		List<UUID> articleIds = fetchedArticles.stream().map(Article::getId).toList();

		var viewCounts = articleViewRepository.findViewCountsByArticleIds(articleIds)
			.stream().collect(Collectors.toMap(v -> v.getArticleId(), v -> v.getViewCount()));

		var viewedArticleIds = articleViewRepository.findArticleIdsViewedByUser(articleIds, currentUserId);

		var commentCounts = commentRepository.findCommentCountsByArticleIds(articleIds)
			.stream().collect(Collectors.toMap(c -> c.getArticleId(), c -> c.getCommentCount()));

		List<ArticleResponse> enrichedResponses = allResponses.stream().map(resp ->
			new ArticleResponse(
				resp.id(),
				resp.title(),
				resp.summary(),
				resp.sourceUrl(),
				resp.publishDate(),
				commentCounts.getOrDefault(resp.id(), resp.commentCount() != null ? resp.commentCount() : 0L),
				viewCounts.getOrDefault(resp.id(), resp.viewCount() != null ? resp.viewCount() : 0L),
				viewedArticleIds.contains(resp.id())
			)
		).toList();

		ArticleSortType sortBy;
		try {
			sortBy = ArticleSortType.valueOf(
				Optional.ofNullable(request.orderBy()).orElse("DATE").toUpperCase()
			);
		} catch (IllegalArgumentException e) {
			sortBy = ArticleSortType.DATE;
		}

		String direction = Optional.ofNullable(request.direction()).orElse("DESC").toUpperCase();

		String nextCursor = null;
		String nextAfterString = null;
		List<ArticleResponse> finalContentList = enrichedResponses;

		if (hasNext) {
			ArticleResponse nextCursorArticle = enrichedResponses.get(requestedSize);
			nextCursor = nextCursorArticle.id().toString();

			switch (sortBy) {
				case DATE -> nextAfterString = nextCursorArticle.publishDate().toString();
				case COMMENT_COUNT -> nextAfterString = String.valueOf(
					nextCursorArticle.commentCount() != null ? nextCursorArticle.commentCount() : 0
				);
				case VIEW_COUNT -> nextAfterString = String.valueOf(
					nextCursorArticle.viewCount() != null ? nextCursorArticle.viewCount() : 0
				);
			}

			finalContentList = enrichedResponses.subList(0, requestedSize);
		} else if (!enrichedResponses.isEmpty() &&
			(sortBy == ArticleSortType.VIEW_COUNT || sortBy == ArticleSortType.COMMENT_COUNT)) {

			ArticleResponse lastArticle = enrichedResponses.get(enrichedResponses.size() - 1);

			if (sortBy == ArticleSortType.VIEW_COUNT) {
				nextAfterString = String.valueOf(lastArticle.viewCount() != null ? lastArticle.viewCount() : 0);
			} else {
				nextAfterString = String.valueOf(lastArticle.commentCount() != null ? lastArticle.commentCount() : 0);
			}
		}

		if (finalContentList.isEmpty()
			&& request.keyword() != null && !request.keyword().isBlank()
			&& (request.cursor() == null || request.cursor().isBlank())) {
			throw new ArticleNotFoundException("검색 결과가 없습니다.");
		}

		return new CursorPageResponseArticleDto(
			finalContentList,
			nextCursor,
			nextAfterString != null ? nextAfterString :
				(request.after() != null ? request.after().toString() : null),
			requestedSize,
			hasNext,
			totalElements
		);
	}

	@Transactional
	public ArticleResponse getArticleById(UUID articleId, UUID userId) {
		Article article = articleRepositoryCustom.findArticleById(articleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

		if (userId != null && !articleViewRepository.existsByArticleIdAndUserId(articleId, userId)) {
			User userRef = entityManager.getReference(User.class, userId);
			articleViewRepository.save(ArticleView.of(article, userRef));
		}

		long viewCount = articleViewRepository.countByArticleId(articleId);
		long commentCount = commentRepository.countByArticleId(articleId);
		boolean viewedByMe = userId != null && articleViewRepository.existsByArticleIdAndUserId(articleId, userId);

		return articleMapper.toResponse(article, viewCount, commentCount, viewedByMe);
	}

	public List<ArticleSourceDto> getAllSources() {
		return Arrays.stream(ArticleSource.values())
			.map(source -> new ArticleSourceDto(source.name()))
			.toList();
	}

	@Transactional
	public void addArticleView(UUID articleId, UUID userId) {
		System.out.println("Received User ID in Service: " + userId);

		Article article = articleRepositoryCustom.findArticleById(articleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

		if (userId != null && !articleViewRepository.existsByArticleIdAndUserId(articleId, userId)) {
			User userRef = entityManager.getReference(User.class, userId);
			articleViewRepository.save(ArticleView.of(article, userRef));
		}
	}

	@Transactional
	public void softDeleteArticle(UUID articleId) {
		Article article = articleRepositoryCustom.findByIdAndDeletedAtIsNull(articleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
		System.out.println(entityManager.contains(article));
		article.softDelete();
		articleRepository.save(article);
	}

	@Transactional
	public void hardDeleteArticle(UUID articleId) {
		Article article = articleRepository.findById(articleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

		articleRepository.delete(article);
	}

	@Transactional
	public List<ArticleRestoreResult> restoreArticles(LocalDateTime from, LocalDateTime to) {
		List<ArticleRestoreResult> results = new ArrayList<>();
		LocalDateTime current = from;

		while (!current.isAfter(to)) {
			try {
				List<ArticleSaveDto> backupArticles = s3BinaryStorage.getBackupArticles(current);

				if (backupArticles.isEmpty()) {
					log.warn("{} 날짜 백업 파일이 없습니다.",
						current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
					current = current.plusDays(1);
					continue;
				}

				log.info("{} 날짜 백업 파일 {}건 로드 완료",
					current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
					backupArticles.size());

				List<ArticleSource> backupSources = backupArticles.stream()
					.map(ArticleSaveDto::getSource)
					.distinct()
					.toList();

				List<String> backupSourceUrls = backupArticles.stream()
					.map(ArticleSaveDto::getSourceUrl)
					.toList();

				List<Article> existingArticles = articleRepository
					.findBySourceInAndSourceUrlIn(backupSources, backupSourceUrls);

				Set<String> existingLinks = existingArticles.stream()
					.map(Article::getOriginalLink)
					.collect(Collectors.toSet());

				List<Article> newArticles = backupArticles.stream()
					.filter(dto -> !existingLinks.contains(dto.getOriginalLink()))
					.map(Article::fromDto)
					.collect(Collectors.toList());

				if (!newArticles.isEmpty()) {
					articleRepository.saveAll(newArticles);

					results.add(new ArticleRestoreResult(
						LocalDateTime.now(),
						newArticles.stream().map(Article::getId).toList(),
						newArticles.size()
					));

					log.info("{} 날짜 복구 완료 (총 {}건 중 {}건 신규 저장)",
						current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
						backupArticles.size(),
						newArticles.size());
				} else {
					log.info("{} 날짜 모든 기사가 이미 존재합니다.",
						current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
				}

			} catch (Exception e) {
				log.error("{} 날짜 복구 중 오류 발생",
					current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), e);
			}

			current = current.plusDays(1);
		}

		return results;
	}

}
