package com.monew.monew_server.domain.article.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.monew.monew_server.domain.article.dto.ArticleRequest;
import com.monew.monew_server.domain.article.dto.ArticleResponse;
import com.monew.monew_server.domain.article.dto.ArticleRestoreResult;
import com.monew.monew_server.domain.article.dto.ArticleSaveDto;
import com.monew.monew_server.domain.article.dto.CursorPageResponseArticleDto;
import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.article.entity.ArticleSortType;
import com.monew.monew_server.domain.article.entity.ArticleSource;
import com.monew.monew_server.domain.article.entity.ArticleView;
import com.monew.monew_server.domain.article.mapper.ArticleMapper;
import com.monew.monew_server.domain.article.repository.ArticleInterestRepository;
import com.monew.monew_server.domain.article.repository.ArticleRepository;
import com.monew.monew_server.domain.article.repository.ArticleRepositoryCustom;
import com.monew.monew_server.domain.article.repository.ArticleViewRepository;
import com.monew.monew_server.domain.article.repository.projection.CommentCountProjection;
import com.monew.monew_server.domain.article.repository.projection.ViewCountProjection;
import com.monew.monew_server.domain.article.storage.S3BinaryStorage;
import com.monew.monew_server.domain.comment.repository.CommentRepository;
import com.monew.monew_server.domain.interest.entity.ArticleInterest;
import com.monew.monew_server.domain.interest.entity.Interest;
import com.monew.monew_server.domain.interest.repository.InterestRepository;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user_activity.repository.mongodb.MUserActivityService;
import com.monew.monew_server.domain.user_activity.repository.mongodb.entity.MUserActivity;
import com.monew.monew_server.exception.ArticleException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ArticleService {

	private static final int DEFAULT_PAGE_SIZE = 10;
	private final ArticleRepository articleRepository;
	private final ArticleRepositoryCustom articleRepositoryCustom;
	private final ArticleMapper articleMapper;
	private final ArticleViewRepository articleViewRepository;
	private final CommentRepository commentRepository;
	private final S3BinaryStorage s3BinaryStorage;

	private final InterestRepository interestRepository;
	private final ArticleInterestRepository articleInterestRepository;
	private final InterestKeywordRepository interestKeywordRepository;
	@Autowired
	private MUserActivityService mUserActivityService;
	@PersistenceContext
	private EntityManager entityManager;

	public ArticleService(
		ArticleRepository articleRepository,
		@Qualifier("articleRepositoryImpl") ArticleRepositoryCustom articleRepositoryCustom,
		ArticleMapper articleMapper,
		ArticleViewRepository articleViewRepository,
		CommentRepository commentRepository, S3BinaryStorage s3BinaryStorage,
		InterestRepository interestRepository,
		ArticleInterestRepository articleInterestRepository
	) {
		this.articleRepository = articleRepository;
		this.articleRepositoryCustom = articleRepositoryCustom;
		this.articleMapper = articleMapper;
		this.articleViewRepository = articleViewRepository;
		this.commentRepository = commentRepository;
		this.s3BinaryStorage = s3BinaryStorage;
		this.interestRepository = interestRepository;
		this.articleInterestRepository = articleInterestRepository;
	}

	public CursorPageResponseArticleDto fetchArticles(ArticleRequest request, UUID currentUserId) {

		int requestedSize = request.limit() != null ? request.limit() : DEFAULT_PAGE_SIZE;
		int fetchSize = requestedSize + 1;

		List<Article> fetchedArticles = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, fetchSize);
		long totalElements = articleRepositoryCustom.countArticlesWithFilter(request);
		boolean hasNext = fetchedArticles.size() > requestedSize;

		List<ArticleResponse> allResponses = articleMapper.toResponseList(fetchedArticles);
		List<UUID> articleIds = fetchedArticles.stream().map(Article::getId).toList();

		var viewCounts = articleViewRepository.findViewCountsByArticleIds(articleIds)
			.stream().collect(Collectors.toMap(ViewCountProjection::getArticleId, ViewCountProjection::getViewCount));

		var viewedArticleIds = articleViewRepository.findArticleIdsViewedByUser(articleIds, currentUserId);

		var commentCounts = commentRepository.findCommentCountsByArticleIds(articleIds)
			.stream().collect(Collectors.toMap(CommentCountProjection::getArticleId,
				CommentCountProjection::getCommentCount));

		List<ArticleResponse> enrichedResponses = allResponses.stream().map(resp ->
			new ArticleResponse(
				resp.id(),
				resp.source(),
				resp.sourceUrl(),
				resp.title(),
				resp.publishDate(),
				resp.summary(),
				commentCounts.getOrDefault(resp.id(), resp.commentCount() != null ? resp.commentCount() : 0L),
				viewCounts.getOrDefault(resp.id(), resp.viewCount() != null ? resp.viewCount() : 0L),
				viewedArticleIds.contains(resp.id())
			)
		).toList();

		ArticleSortType sortBy = parseSortType(request.orderBy());

		log.info("Parsed sortBy: {} from orderBy: {}", sortBy, request.orderBy());

		String nextCursor = null;
		String nextAfterString = null;
		List<ArticleResponse> finalContentList = enrichedResponses;

		if (hasNext) {
			ArticleResponse lastArticle = enrichedResponses.get(requestedSize);
			switch (sortBy) {
				case DATE -> nextCursor = lastArticle.publishDate().toString();
				case COMMENT_COUNT -> nextCursor = String.valueOf(
					lastArticle.commentCount() != null ? lastArticle.commentCount() : 0L
				);
				case VIEW_COUNT -> nextCursor = String.valueOf(
					lastArticle.viewCount() != null ? lastArticle.viewCount() : 0L
				);
			}

			nextAfterString = lastArticle.publishDate().toString();

			log.info("Generated nextCursor: {}, nextAfter: {} for sortBy: {}",
				nextCursor, nextAfterString, sortBy);

			finalContentList = enrichedResponses.subList(0, requestedSize);
		}

		if (finalContentList.isEmpty()
			&& request.keyword() != null && !request.keyword().isBlank()
			&& (request.cursor() == null || request.cursor().isBlank())) {
			throw new ArticleException();
		}

		return new CursorPageResponseArticleDto(
			finalContentList,
			nextCursor,
			nextAfterString,
			requestedSize,
			hasNext,
			totalElements
		);
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

	@Transactional
	public ArticleResponse getArticleById(UUID articleId, UUID userId) {
		Article article = articleRepositoryCustom.findArticleById(articleId)
			.orElseThrow(ArticleException::new);

		boolean viewedByMe = false;

		if (userId != null) {
			viewedByMe = articleViewRepository.existsByArticleIdAndUserId(articleId, userId);

			if (!viewedByMe) {
				ArticleView saved = saveArticleViewToRdb(article, userId);
			}
		}

		long viewCount = articleViewRepository.countByArticleId(articleId);
		long commentCount = commentRepository.countByArticleId(articleId);

		return articleMapper.toResponse(article, viewCount, commentCount, viewedByMe);
	}

	@Transactional
	public ArticleResponse getArticleByIdMongo(UUID articleId, UUID userId) {
		Article article = articleRepositoryCustom.findArticleById(articleId)
			.orElseThrow(ArticleException::new);

		boolean viewedByMe = false;

		if (userId != null) {
			viewedByMe = articleViewRepository.existsByArticleIdAndUserId(articleId, userId);

			if (!viewedByMe) {
				ArticleView saved = saveArticleViewToRdb(article, userId);
				addMongoArticleView(userId, saved);
			}
		}

		long viewCount = articleViewRepository.countByArticleId(articleId);
		long commentCount = commentRepository.countByArticleId(articleId);

		return articleMapper.toResponse(article, viewCount, commentCount, viewedByMe);
	}

	public List<String> getAllSources() {
		return Arrays.stream(ArticleSource.values())
			.map(ArticleSource::name)
			.toList();
	}

	@Transactional
	public void addArticleView(UUID articleId, UUID userId) {
		System.out.println("Received User ID in Service: " + userId);

		Article article = articleRepositoryCustom.findArticleById(articleId)
			.orElseThrow(ArticleException::new);

		if (userId != null && !articleViewRepository.existsByArticleIdAndUserId(articleId, userId)) {
			User userRef = entityManager.getReference(User.class, userId);
			ArticleView save = articleViewRepository.save(ArticleView.of(article, userRef));
			addMongoArticleView(userId, save);
		}
	}

	@Transactional
	public void softDeleteArticle(UUID articleId) {
		Article article = articleRepositoryCustom.findByIdAndDeletedAtIsNull(articleId)
			.orElseThrow(ArticleException::new);
		System.out.println(entityManager.contains(article));
		article.softDelete();
		articleRepository.save(article);
	}

	@Transactional
	public void hardDeleteArticle(UUID articleId) {
		Article article = articleRepository.findById(articleId)
			.orElseThrow(ArticleException::new);

		articleRepository.delete(article);
	}

	@Transactional
	public List<ArticleRestoreResult> restoreArticles(LocalDateTime from, LocalDateTime to) {
		List<ArticleRestoreResult> results = new ArrayList<>();
		List<String> interestNames = getInterestName();
		LocalDateTime current = from;

		while (!current.isAfter(to)) {
			List<UUID> restoredArticleIds = new ArrayList<>();

			for (String interestName : interestNames) {
				log.info("게시글 복원 시작 - 관심사: {}, 날짜: {}", interestName, current);

				List<ArticleSaveDto> backupArticles = s3BinaryStorage.getBackupArticles(interestName, current);

				if (backupArticles.isEmpty()) {
					log.info("복원할 게시글 없음 - 관심사: {}, 날짜: {}", interestName, current);
					continue;
				}

				for (ArticleSaveDto articleSaveDto : backupArticles) {
					if (articleRepository.existsById(articleSaveDto.getId())) {
						log.info("이미 존재하는 게시글 건너뛰기: {}", articleSaveDto.getId());
						continue;
					}

					articleRepository.insertIfNotExists(
						articleSaveDto.getId(),
						articleSaveDto.getSource().name(),
						articleSaveDto.getSourceUrl(),
						articleSaveDto.getTitle(),
						articleSaveDto.getSummary(),
						articleSaveDto.getPublishDate()
					);

					Article article = articleRepository.findById(articleSaveDto.getId()).orElseThrow();
					List<Interest> interests = interestRepository.findByName(interestName);

					for (Interest interest : interests) {
						ArticleInterest articleInterest = ArticleInterest.of(article, interest);
						articleInterestRepository.save(articleInterest);
					}

					restoredArticleIds.add(articleSaveDto.getId());
				}
			}
			ArticleRestoreResult articleRestoreResult = ArticleRestoreResult.builder()
				.restoredArticleCount(restoredArticleIds.size())
				.restoredArticleIds(restoredArticleIds)
				.restoreDate(current)
				.build();
			results.add(articleRestoreResult);

			log.info("{} - 복원 - {}개 게시글 복원", articleRestoreResult.restoreDate(),
				articleRestoreResult.restoredArticleCount());

			current = current.plusDays(1);
		}

		log.info("전체 복원 완료 - 총 {}일 처리, 총 {}개 게시글 복원",
			results.size(),
			results.stream().mapToInt(ArticleRestoreResult::restoredArticleCount).sum());

		return results;
	}

	// mongo
	public void addMongoArticleView(UUID userId, ArticleView articleView) {
		MUserActivity.ArticleView build = MUserActivity.ArticleView.builder()
			.id(articleView.getId())
			.viewedBy(userId)
			.createdAt(articleView.getCreatedAt())
			.articleId(articleView.getArticle().getId())
			.source(articleView.getArticle().getSource().name())
			.sourceUrl(articleView.getArticle().getSourceUrl())
			.articleTitle(articleView.getArticle().getTitle())
			.articlePublishedDate(articleView.getArticle().getPublishDate())
			.articleSummary(articleView.getArticle().getSummary())
			.articleCommentCount((int)commentRepository.countByArticleId(articleView.getArticle().getId()))
			.articleViewCount((int)articleViewRepository.countByArticleId(articleView.getArticle().getId())).build();

		mUserActivityService.addArticleView(userId, build);
	}

	private ArticleView saveArticleViewToRdb(Article article, UUID userId) {
		User userRef = entityManager.getReference(User.class, userId);
		return articleViewRepository.save(ArticleView.of(article, userRef));
	private List<String> getInterestName() {
		List<Interest> all = interestRepository.findAll();
		if (all.isEmpty())
			return List.of();

		return all.stream().map(Interest::getName).toList();
	}
}
