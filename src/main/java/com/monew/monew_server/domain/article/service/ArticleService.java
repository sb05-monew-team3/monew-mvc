package com.monew.monew_server.domain.article.service;

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
import com.monew.monew_server.domain.interest.entity.InterestKeyword;
import com.monew.monew_server.domain.interest.repository.InterestKeywordRepository;
import com.monew.monew_server.domain.interest.repository.InterestRepository;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.exception.ArticleException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ArticleService {

	private final ArticleRepository articleRepository;
	private final ArticleRepositoryCustom articleRepositoryCustom;
	private final ArticleMapper articleMapper;
	private final ArticleViewRepository articleViewRepository;
	private final CommentRepository commentRepository;
	private final S3BinaryStorage s3BinaryStorage;
	private final InterestRepository interestRepository;
	private final ArticleInterestRepository articleInterestRepository;
	private final InterestKeywordRepository interestKeywordRepository;

	public ArticleService(
		ArticleRepository articleRepository,
		@Qualifier("articleRepositoryImpl") ArticleRepositoryCustom articleRepositoryCustom,
		ArticleMapper articleMapper,
		ArticleViewRepository articleViewRepository,
		CommentRepository commentRepository, S3BinaryStorage s3BinaryStorage,
		InterestRepository interestRepository, ArticleInterestRepository articleInterestRepository,
		InterestKeywordRepository interestKeywordRepository
	) {
		this.articleRepository = articleRepository;
		this.articleRepositoryCustom = articleRepositoryCustom;
		this.articleMapper = articleMapper;
		this.articleViewRepository = articleViewRepository;
		this.commentRepository = commentRepository;
		this.s3BinaryStorage = s3BinaryStorage;
		this.interestRepository = interestRepository;
		this.articleInterestRepository = articleInterestRepository;
		this.interestKeywordRepository = interestKeywordRepository;
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

		if (userId != null && !articleViewRepository.existsByArticleIdAndUserId(articleId, userId)) {
			User userRef = entityManager.getReference(User.class, userId);
			articleViewRepository.save(ArticleView.of(article, userRef));
		}

		long viewCount = articleViewRepository.countByArticleId(articleId);
		long commentCount = commentRepository.countByArticleId(articleId);
		boolean viewedByMe = userId != null && articleViewRepository.existsByArticleIdAndUserId(articleId, userId);

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
			articleViewRepository.save(ArticleView.of(article, userRef));
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
		LocalDateTime current = from;

		List<Interest> allInterests = interestRepository.findAll();

		if (allInterests.isEmpty()) {
			log.warn("DB에 등록된 관심사(Interest)가 없어 기사-관심사 연결을 건너뜁니다.");
		}

		List<UUID> interestIds = allInterests.stream()
			.map(Interest::getId)
			.toList();

		List<InterestKeyword> allKeywords = interestKeywordRepository.findByInterestIdIn(interestIds);

		Map<UUID, List<InterestKeyword>> keywordsByInterestId = allKeywords.stream()
			.collect(Collectors.groupingBy(k -> k.getInterest().getId()));

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

					List<ArticleInterest> articleInterestsToSave = new ArrayList<>();

					if (!allInterests.isEmpty()) {
						for (Article article : newArticles) {
							String titleLower = article.getTitle() != null
								? article.getTitle().toLowerCase()
								: "";
							String summaryLower = article.getSummary() != null
								? article.getSummary().toLowerCase()
								: "";
							String contentToMatch = titleLower + " " + summaryLower;

							for (Interest interest : allInterests) {
								List<InterestKeyword> keywords = keywordsByInterestId
									.getOrDefault(interest.getId(), List.of());

								boolean hasMatchingKeyword = keywords.stream()
									.anyMatch(keyword -> {
										String keywordLower = keyword.getName().toLowerCase();
										return contentToMatch.contains(keywordLower);
									});

								if (hasMatchingKeyword) {
									ArticleInterest articleInterest = ArticleInterest.of(article, interest);
									articleInterestsToSave.add(articleInterest);
									log.debug("기사 '{}' 와 관심사 '{}' 연결",
										article.getTitle(), interest.getName());
								}
							}
						}

						if (!articleInterestsToSave.isEmpty()) {
							articleInterestRepository.saveAll(articleInterestsToSave);
						}
					}

					results.add(new ArticleRestoreResult(
						LocalDateTime.now(),
						newArticles.stream().map(Article::getId).toList(),
						newArticles.size()
					));

					log.info("{} 날짜 복구 완료 (총 {}건 중 {}건 신규 저장, {}개 관심사 연결)",
						current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
						backupArticles.size(),
						newArticles.size(),
						articleInterestsToSave.size()
					);
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
