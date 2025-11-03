package com.monew.monew_server.domain.article.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.monew.monew_server.domain.article.dto.ArticleRequest;
import com.monew.monew_server.domain.article.dto.ArticleResponse;
import com.monew.monew_server.domain.article.dto.ArticleRestoreResult;
import com.monew.monew_server.domain.article.dto.ArticleSaveDto;
import com.monew.monew_server.domain.article.dto.CursorPageResponseArticleDto;
import com.monew.monew_server.domain.article.entity.Article;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArticleServiceTest {

	@Mock
	private ArticleRepository articleRepository;
	@Mock
	private ArticleRepositoryCustom articleRepositoryCustom;
	@Mock
	private ArticleMapper articleMapper;
	@Mock
	private ArticleViewRepository articleViewRepository;
	@Mock
	private InterestRepository interestRepository;
	@Mock
	private ArticleInterestRepository articleInterestRepository;
	@Mock
	private InterestKeywordRepository interestKeywordRepository;
	@Mock
	private CommentRepository commentRepository;
	@Mock
	private EntityManager entityManager;
	@Mock
	private S3BinaryStorage s3BinaryStorage;

	private ArticleService articleService;

	private final UUID DUMMY_USER_ID = UUID.randomUUID();
	private final UUID ARTICLE_ID = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		articleService = new ArticleService(
			articleRepository,
			articleRepositoryCustom,
			articleMapper,
			articleViewRepository,
			commentRepository,
			s3BinaryStorage,
			interestRepository,
			articleInterestRepository,
			interestKeywordRepository
		);
		try {
			java.lang.reflect.Field entityManagerField = ArticleService.class.getDeclaredField("entityManager");
			entityManagerField.setAccessible(true);
			entityManagerField.set(articleService, entityManager);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException("Failed to inject EntityManager", e);
		}

		when(articleViewRepository.findViewCountsByArticleIds(anyList())).thenReturn(Collections.emptyList());
		when(articleViewRepository.findArticleIdsViewedByUser(anyList(), any())).thenReturn(Collections.emptySet());
		when(commentRepository.findCommentCountsByArticleIds(anyList())).thenReturn(Collections.emptyList());
	}

	@Test
	@DisplayName("Service - 키워드로 기사 검색 및 hasNext true")
	void shouldReturnArticlesByKeyword() {
		ArticleRequest request = new ArticleRequest(
			"삼성", null, null, null, null, "DATE", "DESC", null,
			LocalDateTime.parse("2025-10-27T10:30:00"), 10
		);

		List<Article> mockArticles = IntStream.range(0, 11)
			.mapToObj(i -> Article.builder()
				.id(UUID.randomUUID())
				.title("삼성전자 뉴스 " + i)
				.summary("내용 " + i)
				.source(ArticleSource.NAVER)
				.sourceUrl("http://test.com/" + i)
				.createdAt(Instant.now().plus(i, ChronoUnit.MINUTES))
				.publishDate(Instant.now().plus(i, ChronoUnit.MINUTES))
				.build())
			.collect(Collectors.toList());

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(mockArticles);
		when(articleMapper.toResponseList(anyList())).thenReturn(mockArticles.stream()
			.map(a -> new ArticleResponse(a.getId(), a.getSource(), a.getSourceUrl(), a.getTitle(), a.getPublishDate(),
				a.getSummary(), 0L, 0L, false))
			.toList());
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(100L);

		CursorPageResponseArticleDto response = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(response.getContent()).hasSize(10);
		assertThat(response.isHasNext()).isTrue();
		assertThat(response.getNextCursor()).isEqualTo(mockArticles.get(10).getPublishDate().toString());
		assertThat(response.getNextAfter()).isEqualTo(mockArticles.get(10).getPublishDate().toString());
	}

	@Test
	@DisplayName("Service - 키워드 검색 결과가 0개일 때 ArticleException 발생")
	void shouldThrowExceptionWhenKeywordSearchHasNoResults() {
		ArticleRequest request = new ArticleRequest(
			"없는키워드", null, null, null, null, "DATE", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(Collections.emptyList());
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(0L);

		assertThatThrownBy(() -> articleService.fetchArticles(request, DUMMY_USER_ID))
			.isInstanceOf(ArticleException.class)
			.hasMessageContaining("기사를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("Service - sortBy null일 때 DATE 정렬을 기본값으로 사용")
	void shouldUseDateSortAsDefault() {
		Instant publishDate = Instant.now();
		Article a1 = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(publishDate)
			.build();
		Article a2 = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(publishDate.minusSeconds(1))
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, null, "DESC", null, null, 1
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(any(ArticleRequest.class), eq(2)))
			.thenReturn(List.of(a1, a2));
		when(articleRepositoryCustom.countArticlesWithFilter(any(ArticleRequest.class))).thenReturn(2L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(a1.getId(), ArticleSource.NAVER, "url", "A1", a1.getPublishDate(), "S", 0L, 0L, false),
			new ArticleResponse(a2.getId(), ArticleSource.NAVER, "url", "A2", a2.getPublishDate(), "S", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isTrue();
		assertThat(dto.getNextAfter()).isEqualTo(a2.getPublishDate().toString());
		assertThat(dto.getNextCursor()).isEqualTo(a2.getPublishDate().toString());
	}

	@Test
	@DisplayName("Service - sortBy VIEW_COUNT 처리 및 hasNext true")
	void shouldHandleViewCountSortWithHasNext() {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();

		Instant now = Instant.now();
		Instant nowPlusOne = now.plusSeconds(1);

		Article a1 = Article.builder().id(id1).source(ArticleSource.NAVER).publishDate(now).build();
		Article a2 = Article.builder().id(id2).source(ArticleSource.NAVER).publishDate(nowPlusOne).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "VIEW_COUNT", "DESC", null, null, 1
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 2)).thenReturn(List.of(a1, a2));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(2L);

		when(articleViewRepository.findViewCountsByArticleIds(anyList())).thenReturn(List.of(
			new ViewCountProjection() {
				public UUID getArticleId() {
					return id1;
				}

				public long getViewCount() {
					return 10L;
				}
			},
			new ViewCountProjection() {
				public UUID getArticleId() {
					return id2;
				}

				public long getViewCount() {
					return 5L;
				}
			}
		));

		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id1, ArticleSource.NAVER, "url", "A1", now, "S1", 0L, 10L, false),
			new ArticleResponse(id2, ArticleSource.NAVER, "url", "A2", nowPlusOne, "S2", 0L, 5L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isTrue();
		assertThat(dto.getNextCursor()).isEqualTo("5");
		assertThat(dto.getNextAfter()).isEqualTo(nowPlusOne.toString());
	}

	@Test
	@DisplayName("Service - sortBy COMMENT_COUNT 처리 및 hasNext false")
	void shouldHandleCommentCountSortWithNoNextPage() {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();

		Instant now = Instant.now();
		Article a1 = Article.builder().id(id1).source(ArticleSource.NAVER).publishDate(now).build();
		Article a2 = Article.builder().id(id2).source(ArticleSource.NAVER).publishDate(now.plusSeconds(1)).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "COMMENT_COUNT", "DESC", null, null, 2
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 3)).thenReturn(List.of(a1, a2));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(2L);

		when(commentRepository.findCommentCountsByArticleIds(anyList())).thenReturn(List.of(
			new CommentCountProjection() {
				public UUID getArticleId() {
					return id1;
				}

				public Long getCommentCount() {
					return 3L;
				}
			},
			new CommentCountProjection() {
				public UUID getArticleId() {
					return id2;
				}

				public Long getCommentCount() {
					return 3L;
				}
			}
		));

		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id1, ArticleSource.NAVER, "url", "A1", now, "S1", 3L, 0L, false),
			new ArticleResponse(id2, ArticleSource.NAVER, "url", "A2", now.plusSeconds(1), "S2", 3L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isFalse();
		assertThat(dto.getNextCursor()).isNull();
		assertThat(dto.getNextAfter()).isNull();
		assertThat(dto.getSize()).isEqualTo(2);
	}

	@Test
	@DisplayName("Service - 잘못된 sortBy 값일 때 DATE로 폴백")
	void shouldFallbackToDateWhenInvalidSortBy() {
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "INVALID_SORT", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article.getId(), ArticleSource.NAVER, "url", "Title", article.getPublishDate(),
				"Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
		assertThat(dto.isHasNext()).isFalse();
	}

	@Test
	@DisplayName("Service - direction이 null일 때 DESC를 기본값으로 사용")
	void shouldUseDescAsDefaultDirection() {
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "DATE", null, null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article.getId(), ArticleSource.NAVER, "url", "Title", article.getPublishDate(),
				"Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
	}

	@Test
	@DisplayName("Service - limit이 null일 때 기본 페이지 크기 10 사용")
	void shouldUseDefaultPageSizeWhenLimitIsNull() {
		List<Article> articles = IntStream.range(0, 5)
			.mapToObj(i -> Article.builder()
				.id(UUID.randomUUID())
				.source(ArticleSource.NAVER)
				.publishDate(Instant.now())
				.build())
			.collect(Collectors.toList());

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "DATE", "DESC", null, null, null
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(articles);
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(5L);
		when(articleMapper.toResponseList(anyList())).thenReturn(articles.stream()
			.map(a -> new ArticleResponse(a.getId(), ArticleSource.NAVER, "url", "Title", a.getPublishDate(), "Summary",
				0L, 0L, false))
			.collect(Collectors.toList()));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(5);
		assertThat(dto.getSize()).isEqualTo(10);
	}

	@Test
	@DisplayName("Service - VIEW_COUNT 정렬이고 hasNext가 false인 경우 nextAfter 설정")
	void shouldSetNextAfterWhenViewCountSortAndNoNextPage() {
		UUID id1 = UUID.randomUUID();
		Instant now = Instant.now();
		Article article = Article.builder().id(id1).publishDate(now).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "VIEW_COUNT", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleViewRepository.findViewCountsByArticleIds(anyList())).thenReturn(List.of(
			new ViewCountProjection() {
				public UUID getArticleId() {
					return id1;
				}

				public long getViewCount() {
					return 7L;
				}
			}
		));
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id1, ArticleSource.NAVER, "url", "Title", now, "Summary", 0L, 7L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isFalse();
		assertThat(dto.getNextAfter()).isNull();
	}

	@Test
	@DisplayName("Service - COMMENT_COUNT 정렬이고 hasNext가 false인 경우 nextAfter 설정")
	void shouldSetNextAfterWhenCommentCountSortAndNoNextPage() {
		UUID id1 = UUID.randomUUID();
		Instant now = Instant.now();
		Article article = Article.builder().id(id1).publishDate(now).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "COMMENT_COUNT", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(commentRepository.findCommentCountsByArticleIds(anyList())).thenReturn(List.of(
			new CommentCountProjection() {
				public UUID getArticleId() {
					return id1;
				}

				public Long getCommentCount() {
					return 8L;
				}
			}
		));
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id1, ArticleSource.NAVER, "url", "Title", now, "Summary", 8L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isFalse();
		assertThat(dto.getNextAfter()).isNull();
	}

	@Test
	@DisplayName("Service - 키워드 없고 cursor 있을 때는 예외 발생 안함")
	void shouldNotThrowExceptionWhenNoKeywordButHasCursor() {
		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "DATE", "DESC", "some-cursor", null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(Collections.emptyList());
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(0L);

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).isEmpty();
		assertThat(dto.isHasNext()).isFalse();
	}

	@Test
	@DisplayName("Service - 단일 기사 조회 성공")
	void shouldGetArticleById() {
		Article article = Article.builder()
			.id(ARTICLE_ID)
			.title("Test Article")
			.build();

		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(articleViewRepository.countByArticleId(ARTICLE_ID)).thenReturn(5L);
		when(commentRepository.countByArticleId(ARTICLE_ID)).thenReturn(3L);
		when(articleViewRepository.existsByArticleIdAndUserId(ARTICLE_ID, DUMMY_USER_ID)).thenReturn(true);
		when(articleMapper.toResponse(article, 5L, 3L, true)).thenReturn(
			new ArticleResponse(ARTICLE_ID, ArticleSource.NAVER, "url", "Test Article", Instant.now(), "Summary", 3L,
				5L, true)
		);

		ArticleResponse response = articleService.getArticleById(ARTICLE_ID, DUMMY_USER_ID);

		assertThat(response.id()).isEqualTo(ARTICLE_ID);
		assertThat(response.viewCount()).isEqualTo(5L);
		assertThat(response.commentCount()).isEqualTo(3L);
		assertThat(response.viewedByMe()).isTrue();
	}

	@Test
	@DisplayName("Service - 단일 기사 조회 실패 시 예외 발생")
	void shouldThrowExceptionWhenArticleNotFound() {
		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> articleService.getArticleById(ARTICLE_ID, DUMMY_USER_ID))
			.isInstanceOf(ArticleException.class)
			.hasMessageContaining("기사를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("Service - userId가 null이면 조회수 추가 안함")
	void shouldNotAddViewWhenUserIdIsNull() {
		Article article = Article.builder().id(ARTICLE_ID).build();

		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(articleViewRepository.countByArticleId(ARTICLE_ID)).thenReturn(0L);
		when(commentRepository.countByArticleId(ARTICLE_ID)).thenReturn(0L);
		when(articleMapper.toResponse(article, 0L, 0L, false)).thenReturn(
			new ArticleResponse(ARTICLE_ID, ArticleSource.NAVER, "url", "Title", Instant.now(), "Summary", 0L, 0L,
				false)
		);

		articleService.getArticleById(ARTICLE_ID, null);

		verify(articleViewRepository, never()).save(any());
	}

	@Test
	@DisplayName("Service - 이미 조회한 기사는 조회수 추가 안함")
	void shouldNotAddViewWhenAlreadyViewed() {
		Article article = Article.builder().id(ARTICLE_ID).build();

		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(articleViewRepository.existsByArticleIdAndUserId(ARTICLE_ID, DUMMY_USER_ID)).thenReturn(true);
		when(articleViewRepository.countByArticleId(ARTICLE_ID)).thenReturn(1L);
		when(commentRepository.countByArticleId(ARTICLE_ID)).thenReturn(0L);
		when(articleMapper.toResponse(eq(article), eq(1L), eq(0L), eq(true))).thenReturn(
			new ArticleResponse(ARTICLE_ID, ArticleSource.NAVER, "url", "Title", Instant.now(), "Summary", 0L, 1L, true)
		);

		articleService.getArticleById(ARTICLE_ID, DUMMY_USER_ID);

		verify(articleViewRepository, times(2)).existsByArticleIdAndUserId(ARTICLE_ID, DUMMY_USER_ID);
		verify(articleViewRepository, never()).save(any());
	}

	@Test
	@DisplayName("Service - 단일 기사 조회 시 새로운 조회면 ArticleView 추가")
	void shouldAddNewArticleViewWhenNotViewedYet() {
		Article article = Article.builder().id(ARTICLE_ID).build();
		User user = User.builder().id(DUMMY_USER_ID).build();

		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(articleViewRepository.existsByArticleIdAndUserId(ARTICLE_ID, DUMMY_USER_ID))
			.thenReturn(false)
			.thenReturn(true);
		when(entityManager.getReference(User.class, DUMMY_USER_ID)).thenReturn(user);
		when(articleViewRepository.countByArticleId(ARTICLE_ID)).thenReturn(1L);
		when(commentRepository.countByArticleId(ARTICLE_ID)).thenReturn(0L);
		when(articleMapper.toResponse(article, 1L, 0L, true)).thenReturn(
			new ArticleResponse(ARTICLE_ID, ArticleSource.NAVER, "url", "Title", Instant.now(), "Summary", 0L, 1L, true)
		);

		articleService.getArticleById(ARTICLE_ID, DUMMY_USER_ID);

		verify(articleViewRepository).save(any(ArticleView.class));
		verify(entityManager).getReference(User.class, DUMMY_USER_ID);
	}

	@Test
	@DisplayName("Service - 모든 기사 소스 조회")
	void shouldGetAllSources() {
		List<String> sources = articleService.getAllSources();

		assertThat(sources).isNotEmpty();
		assertThat(sources).allMatch(name -> name != null);
		assertThat(sources).contains("NAVER", "HANKYUNG", "CHOSUN", "YEONHAP");
	}

	@Test
	@DisplayName("Service - 조회수 추가 - userId null이면 저장 안함")
	void shouldNotSaveViewWhenUserIdIsNullInAddArticleView() {
		Article article = Article.builder().id(ARTICLE_ID).build();

		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.of(article));

		articleService.addArticleView(ARTICLE_ID, null);

		verify(articleViewRepository, never()).save(any());
	}

	@Test
	@DisplayName("Service - 조회수 추가 - 이미 조회한 기사면 저장 안함")
	void shouldNotSaveViewWhenAlreadyViewedInAddArticleView() {
		Article article = Article.builder().id(ARTICLE_ID).build();

		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(articleViewRepository.existsByArticleIdAndUserId(ARTICLE_ID, DUMMY_USER_ID)).thenReturn(true);

		articleService.addArticleView(ARTICLE_ID, DUMMY_USER_ID);

		verify(articleViewRepository, never()).save(any());
	}

	@Test
	@DisplayName("Service - 조회수 추가 - 새로운 조회면 저장")
	void shouldSaveViewWhenNotViewedYet() {
		Article article = Article.builder().id(ARTICLE_ID).build();
		User user = User.builder().id(DUMMY_USER_ID).build();

		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(articleViewRepository.existsByArticleIdAndUserId(ARTICLE_ID, DUMMY_USER_ID)).thenReturn(false);
		when(entityManager.getReference(User.class, DUMMY_USER_ID)).thenReturn(user);

		articleService.addArticleView(ARTICLE_ID, DUMMY_USER_ID);

		verify(articleViewRepository).save(any(ArticleView.class));
	}

	@Test
	@DisplayName("Service - 조회수 추가 시 기사 없으면 예외")
	void shouldThrowExceptionWhenArticleNotFoundInAddView() {
		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> articleService.addArticleView(ARTICLE_ID, DUMMY_USER_ID))
			.isInstanceOf(ArticleException.class)
			.hasMessageContaining("기사를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("Service - 소프트 삭제 성공")
	void shouldSoftDeleteArticle() {
		Article article = Article.builder().id(ARTICLE_ID).build();

		when(articleRepositoryCustom.findByIdAndDeletedAtIsNull(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(entityManager.contains(article)).thenReturn(true);

		articleService.softDeleteArticle(ARTICLE_ID);

		verify(articleRepository).save(article);
	}

	@Test
	@DisplayName("Service - 소프트 삭제 시 기사 없으면 예외")
	void shouldThrowExceptionWhenArticleNotFoundInSoftDelete() {
		when(articleRepositoryCustom.findByIdAndDeletedAtIsNull(ARTICLE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> articleService.softDeleteArticle(ARTICLE_ID))
			.isInstanceOf(ArticleException.class)
			.hasMessageContaining("기사를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("Service - 하드 삭제 성공")
	void shouldHardDeleteArticle() {
		Article article = Article.builder().id(ARTICLE_ID).build();

		when(articleRepository.findById(ARTICLE_ID)).thenReturn(Optional.of(article));

		articleService.hardDeleteArticle(ARTICLE_ID);

		verify(articleRepository).delete(article);
	}

	@Test
	@DisplayName("Service - 하드 삭제 시 기사 없으면 예외")
	void shouldThrowExceptionWhenArticleNotFoundInHardDelete() {
		when(articleRepository.findById(ARTICLE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> articleService.hardDeleteArticle(ARTICLE_ID))
			.isInstanceOf(ArticleException.class)
			.hasMessageContaining("기사를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("Service - fetchArticles에서 commentCount가 null인 경우 0으로 처리")
	void shouldHandleNullCommentCountInFetchArticles() {
		UUID id = UUID.randomUUID();
		Article article = Article.builder().id(id).source(ArticleSource.NAVER).publishDate(Instant.now()).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "DATE", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id, ArticleSource.NAVER, "url", "Title", Instant.now(), "Summary", null, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
		assertThat(dto.getContent().get(0).commentCount()).isEqualTo(0L);
	}

	@Test
	@DisplayName("Service - fetchArticles에서 viewCount가 null인 경우 0으로 처리")
	void shouldHandleNullViewCountInFetchArticles() {
		UUID id = UUID.randomUUID();
		Article article = Article.builder().id(id).source(ArticleSource.NAVER).publishDate(Instant.now()).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "DATE", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id, ArticleSource.NAVER, "url", "Title", Instant.now(), "Summary", 0L, null, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
		assertThat(dto.getContent().get(0).viewCount()).isEqualTo(0L);
	}

	@Test
	@DisplayName("Service - hasNext true이고 commentCount null일 때 0으로 처리")
	void shouldHandleNullCommentCountWhenHasNext() {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();

		Instant now = Instant.now();
		Article a1 = Article.builder().id(id1).source(ArticleSource.NAVER).publishDate(now).build();
		Article a2 = Article.builder().id(id2).source(ArticleSource.NAVER).publishDate(now.minusSeconds(1)).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "COMMENT_COUNT", "DESC", null, null, 1
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 2)).thenReturn(List.of(a1, a2));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(2L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id1, ArticleSource.NAVER, "url", "A1", now, "S1", null, 0L, false),
			new ArticleResponse(id2, ArticleSource.NAVER, "url", "A2", now.minusSeconds(1), "S2", null, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isTrue();
		assertThat(dto.getNextCursor()).isEqualTo("0");
		assertThat(dto.getNextAfter()).isEqualTo(now.minusSeconds(1).toString());
	}

	@Test
	@DisplayName("Service - hasNext true이고 viewCount null일 때 0으로 처리")
	void shouldHandleNullViewCountWhenHasNext() {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();

		Instant now = Instant.now();
		Article a1 = Article.builder().id(id1).source(ArticleSource.NAVER).publishDate(now).build();
		Article a2 = Article.builder().id(id2).source(ArticleSource.NAVER).publishDate(now.minusSeconds(1)).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "VIEW_COUNT", "DESC", null, null, 1
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 2)).thenReturn(List.of(a1, a2));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(2L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id1, ArticleSource.NAVER, "url", "A1", now, "S1", 0L, null, false),
			new ArticleResponse(id2, ArticleSource.NAVER, "url", "A2", now.minusSeconds(1), "S2", 0L, null, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isTrue();
		assertThat(dto.getNextCursor()).isEqualTo("0");
		assertThat(dto.getNextAfter()).isEqualTo(now.minusSeconds(1).toString());
	}

	@Test
	@DisplayName("Service - hasNext false이고 enrichedResponses가 비어있지 않고 commentCount null일 때")
	void shouldHandleNullCommentCountWhenNoNextPageAndNotEmpty() {
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		Article article = Article.builder().id(id).source(ArticleSource.NAVER).publishDate(now).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "COMMENT_COUNT", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id, ArticleSource.NAVER, "url", "Title", now, "Summary", null, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isFalse();
		assertThat(dto.getNextAfter()).isNull();
	}

	@Test
	@DisplayName("기사에 viewCount가 null일 때 nextCursor가 '0'으로 올바르게 설정되는지 확인 (hasNext=true)")
	void shouldSetNextCursorToZeroWhenViewCountIsNullAndHasNextIsTrue() {
		UUID id = UUID.randomUUID();
		Article article1 = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now().minusSeconds(10))
			.build();
		Article article2 = Article.builder().id(id).source(ArticleSource.NAVER).publishDate(Instant.now()).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "VIEW_COUNT", "DESC", null, null, 1
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 2)).thenReturn(
			List.of(article1, article2));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(2L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article1.getId(), ArticleSource.NAVER, "u1", "T1", Instant.now().minusSeconds(10), "S1",
				5L, 10L, false),
			new ArticleResponse(article2.getId(), ArticleSource.NAVER, "u2", "T2", Instant.now(), "S2", 0L, null, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isTrue();
		assertThat(dto.getNextCursor()).isEqualTo("0");
		assertThat(dto.getNextAfter()).isNotNull();
	}

	@Test
	@DisplayName("Service - DATE 정렬이고 hasNext false일 때 nextAfter가 null")
	void shouldNotSetNextAfterWhenDateSortAndNoNextPage() {
		UUID id = UUID.randomUUID();
		Article article = Article.builder().id(id).source(ArticleSource.NAVER).publishDate(Instant.now()).build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "DATE", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id, ArticleSource.NAVER, "url", "Title", Instant.now(), "Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isFalse();
		assertThat(dto.getNextAfter()).isNull();
	}

	@Test
	@DisplayName("Service - after 파라미터가 있을 때 nextAfter 설정")
	void shouldUseAfterParameterForNextAfter() {
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		Article article = Article.builder().id(id).source(ArticleSource.NAVER).publishDate(now).build();
		LocalDateTime afterTime = LocalDateTime.now();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null, "DATE", "DESC", null, afterTime, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id, ArticleSource.NAVER, "url", "Title", now, "Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isFalse();
		assertThat(dto.getNextAfter()).isNull();
	}

	@Test
	@DisplayName("Service - restoreArticles 백업 파일이 비어있을 때")
	void shouldSkipWhenBackupArticlesEmpty() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 1, 0, 0);

		when(s3BinaryStorage.getBackupArticles(from)).thenReturn(Collections.emptyList());

		List<com.monew.monew_server.domain.article.dto.ArticleRestoreResult> results =
			articleService.restoreArticles(from, to);

		assertThat(results).isEmpty();
		verify(articleRepository, never()).saveAll(anyList());
	}

	@Test
	@DisplayName("Service - restoreArticles 복구 중 예외 발생")
	void shouldHandleExceptionDuringRestore() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 1, 0, 0);

		when(s3BinaryStorage.getBackupArticles(from)).thenThrow(new RuntimeException("S3 Error"));

		List<com.monew.monew_server.domain.article.dto.ArticleRestoreResult> results =
			articleService.restoreArticles(from, to);

		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("Service - restoreArticles 모든 기사가 이미 존재")
	void shouldSkipWhenAllArticlesAlreadyExist() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 1, 0, 0);

		com.monew.monew_server.domain.article.dto.ArticleSaveDto dto1 =
			com.monew.monew_server.domain.article.dto.ArticleSaveDto.builder()
				.source(ArticleSource.NAVER)
				.sourceUrl("http://test.com/1")
				.title("Article 1")
				.summary("Summary")
				.publishDate(Instant.now())
				.build();

		when(s3BinaryStorage.getBackupArticles(from)).thenReturn(List.of(dto1));

		Article existingArticle = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/1")
			.title("Existing Article")
			.summary("Summary")
			.publishDate(Instant.now())
			.build();

		when(articleRepository.findBySourceInAndSourceUrlIn(anyList(), anyList()))
			.thenReturn(List.of(existingArticle));

		List<com.monew.monew_server.domain.article.dto.ArticleRestoreResult> results =
			articleService.restoreArticles(from, to);

		assertThat(results).isEmpty();
		verify(articleRepository, never()).saveAll(anyList());
	}

	@Test
	@DisplayName("Service - restoreArticles 신규 기사 저장 성공")
	void shouldSaveNewArticlesWhenRestoring() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 1, 0, 0);

		com.monew.monew_server.domain.article.dto.ArticleSaveDto dto1 =
			com.monew.monew_server.domain.article.dto.ArticleSaveDto.builder()
				.source(ArticleSource.NAVER)
				.sourceUrl("http://test.com/1")
				.title("New Article")
				.summary("Summary")
				.publishDate(Instant.now())
				.build();

		when(s3BinaryStorage.getBackupArticles(from)).thenReturn(List.of(dto1));
		when(articleRepository.findBySourceInAndSourceUrlIn(anyList(), anyList()))
			.thenReturn(Collections.emptyList());

		List<com.monew.monew_server.domain.article.dto.ArticleRestoreResult> results =
			articleService.restoreArticles(from, to);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).restoredArticleCount()).isEqualTo(1);
		verify(articleRepository).saveAll(anyList());
	}

	@Test
	@DisplayName("Service - restoreArticles 여러 날짜 처리")
	void shouldProcessMultipleDatesWhenRestoring() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 2, 0, 0);

		com.monew.monew_server.domain.article.dto.ArticleSaveDto dto1 =
			com.monew.monew_server.domain.article.dto.ArticleSaveDto.builder()
				.source(ArticleSource.NAVER)
				.sourceUrl("http://test.com/1")
				.title("Article 1")
				.summary("Summary")
				.publishDate(Instant.now())
				.build();

		com.monew.monew_server.domain.article.dto.ArticleSaveDto dto2 =
			com.monew.monew_server.domain.article.dto.ArticleSaveDto.builder()
				.source(ArticleSource.CHOSUN)
				.sourceUrl("http://test.com/2")
				.title("Article 2")
				.summary("Summary")
				.publishDate(Instant.now())
				.build();

		when(s3BinaryStorage.getBackupArticles(from)).thenReturn(List.of(dto1));
		when(s3BinaryStorage.getBackupArticles(from.plusDays(1))).thenReturn(List.of(dto2));
		when(articleRepository.findBySourceInAndSourceUrlIn(anyList(), anyList()))
			.thenReturn(Collections.emptyList());

		List<com.monew.monew_server.domain.article.dto.ArticleRestoreResult> results =
			articleService.restoreArticles(from, to);

		assertThat(results).hasSize(2);
		verify(articleRepository, times(2)).saveAll(anyList());
	}

	@Test
	@DisplayName("Service - restoreArticles 일부 기사만 신규")
	void shouldSaveOnlyNewArticlesWhenRestoring() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 1, 0, 0);

		com.monew.monew_server.domain.article.dto.ArticleSaveDto newDto =
			com.monew.monew_server.domain.article.dto.ArticleSaveDto.builder()
				.source(ArticleSource.NAVER)
				.sourceUrl("http://test.com/new")
				.title("New Article")
				.summary("Summary")
				.publishDate(Instant.now())
				.build();

		com.monew.monew_server.domain.article.dto.ArticleSaveDto existingDto =
			com.monew.monew_server.domain.article.dto.ArticleSaveDto.builder()
				.source(ArticleSource.CHOSUN)
				.sourceUrl("http://test.com/existing")
				.title("Existing Article")
				.summary("Summary")
				.publishDate(Instant.now())
				.build();

		Article existingArticle = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.CHOSUN)
			.sourceUrl("http://test.com/existing")
			.title("Existing Article")
			.summary("Summary")
			.publishDate(Instant.now())
			.build();

		when(s3BinaryStorage.getBackupArticles(from)).thenReturn(List.of(newDto, existingDto));
		when(articleRepository.findBySourceInAndSourceUrlIn(anyList(), anyList()))
			.thenReturn(List.of(existingArticle));

		List<com.monew.monew_server.domain.article.dto.ArticleRestoreResult> results =
			articleService.restoreArticles(from, to);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).restoredArticleCount()).isEqualTo(1);
		verify(articleRepository).saveAll(argThat(articles -> {
			List<Article> list = (List<Article>)articles;
			return list.size() == 1;
		}));
	}

	@Test
	@DisplayName("Service - 키워드는 있지만 공백만 있을 때 예외 발생 안함")
	void shouldNotThrowExceptionWhenKeywordIsBlank() {
		ArticleRequest request = new ArticleRequest(
			"   ", null, null, null, null, "DATE", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(Collections.emptyList());
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(0L);

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).isEmpty();
		assertThat(dto.isHasNext()).isFalse();
	}

	@Test
	@DisplayName("Service - cursor는 빈 문자열이지만 키워드가 있고 결과가 없을 때 예외")
	void shouldThrowExceptionWhenCursorEmptyButKeywordExists() {
		ArticleRequest request = new ArticleRequest(
			"검색어", null, null, null, null, "DATE", "DESC", "", null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(Collections.emptyList());
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(0L);

		assertThatThrownBy(() -> articleService.fetchArticles(request, DUMMY_USER_ID))
			.isInstanceOf(ArticleException.class);
	}

	@Test
	@DisplayName("parseSortType - 'viewcount' 소문자로 입력 시 VIEW_COUNT 매핑")
	void shouldMapLowercaseViewcount() {
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"viewcount", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article.getId(), ArticleSource.NAVER, "url", "Title", article.getPublishDate(),
				"Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
		assertThat(dto.isHasNext()).isFalse();
	}

	@Test
	@DisplayName("parseSortType - 'commentcount' 소문자로 입력 시 COMMENT_COUNT 매핑")
	void shouldMapLowercaseCommentcount() {
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"commentcount", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article.getId(), ArticleSource.NAVER, "url", "Title", article.getPublishDate(),
				"Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
		assertThat(dto.isHasNext()).isFalse();
	}

	@Test
	@DisplayName("parseSortType - 'date' 소문자로 입력 시 DATE 매핑")
	void shouldMapLowercaseDate() {
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"date", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article.getId(), ArticleSource.NAVER, "url", "Title", article.getPublishDate(),
				"Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
		assertThat(dto.isHasNext()).isFalse();
	}

	@Test
	@DisplayName("parseSortType - 빈 문자열일 때 DATE로 폴백")
	void shouldFallbackToDateWhenOrderByIsBlank() {
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article.getId(), ArticleSource.NAVER, "url", "Title", article.getPublishDate(),
				"Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
		assertThat(dto.isHasNext()).isFalse();
	}

	@Test
	@DisplayName("parseSortType - 공백만 있을 때 DATE로 폴백")
	void shouldFallbackToDateWhenOrderByIsWhitespace() {
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"   ", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11)).thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article.getId(), ArticleSource.NAVER, "url", "Title", article.getPublishDate(),
				"Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
		assertThat(dto.isHasNext()).isFalse();
	}

	@Test
	@DisplayName("softDeleteArticle - entityManager.contains가 false일 때")
	void shouldSoftDeleteWhenEntityNotContained() {
		Article article = Article.builder().id(ARTICLE_ID).build();

		when(articleRepositoryCustom.findByIdAndDeletedAtIsNull(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(entityManager.contains(article)).thenReturn(false);

		articleService.softDeleteArticle(ARTICLE_ID);

		verify(articleRepository).save(article);
		assertThat(article.getDeletedAt()).isNotNull();
	}

	@Test
	@DisplayName("fetchArticles - cursor가 null이 아니고 키워드가 없을 때 빈 결과 반환")
	void shouldReturnEmptyWhenCursorExistsButNoResults() {
		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", "some-cursor", LocalDateTime.now(), 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11))
			.thenReturn(Collections.emptyList());
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(0L);

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).isEmpty();
		assertThat(dto.isHasNext()).isFalse();
	}

	@Test
	@DisplayName("fetchArticles - 키워드가 null이고 결과가 없을 때 예외 발생 안함")
	void shouldNotThrowExceptionWhenNoKeywordAndEmptyResults() {
		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11))
			.thenReturn(Collections.emptyList());
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(0L);

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).isEmpty();
		assertThat(dto.isHasNext()).isFalse();
	}

	@Test
	@DisplayName("getArticleById - entityManager에서 User 참조 가져오기")
	void shouldGetUserReferenceFromEntityManager() {
		Article article = Article.builder().id(ARTICLE_ID).build();
		User user = User.builder().id(DUMMY_USER_ID).build();

		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(articleViewRepository.existsByArticleIdAndUserId(ARTICLE_ID, DUMMY_USER_ID))
			.thenReturn(false)
			.thenReturn(true);
		when(entityManager.getReference(User.class, DUMMY_USER_ID)).thenReturn(user);
		when(articleViewRepository.countByArticleId(ARTICLE_ID)).thenReturn(1L);
		when(commentRepository.countByArticleId(ARTICLE_ID)).thenReturn(0L);
		when(articleMapper.toResponse(article, 1L, 0L, true)).thenReturn(
			new ArticleResponse(ARTICLE_ID, ArticleSource.NAVER, "url", "Title", Instant.now(), "Summary", 0L, 1L, true)
		);

		articleService.getArticleById(ARTICLE_ID, DUMMY_USER_ID);

		verify(entityManager).getReference(User.class, DUMMY_USER_ID);
		verify(articleViewRepository).save(any(ArticleView.class));
	}

	@Test
	@DisplayName("addArticleView - userId가 null이 아니지만 빈 문자열도 아닐 때")
	void shouldAddViewWhenUserIdIsValid() {
		Article article = Article.builder().id(ARTICLE_ID).build();
		User user = User.builder().id(DUMMY_USER_ID).build();

		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(articleViewRepository.existsByArticleIdAndUserId(ARTICLE_ID, DUMMY_USER_ID)).thenReturn(false);
		when(entityManager.getReference(User.class, DUMMY_USER_ID)).thenReturn(user);

		articleService.addArticleView(ARTICLE_ID, DUMMY_USER_ID);

		verify(articleViewRepository).save(any(ArticleView.class));
	}

	@Test
	@DisplayName("fetchArticles - totalElements가 0일 때도 정상 처리")
	void shouldHandleZeroTotalElements() {
		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11))
			.thenReturn(Collections.emptyList());
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(0L);

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getTotalElements()).isEqualTo(0L);
		assertThat(dto.getContent()).isEmpty();
	}

	@Test
	@DisplayName("fetchArticles - enrichedResponses 처리 시 모든 필드 올바르게 매핑")
	void shouldCorrectlyMapAllFieldsInEnrichedResponses() {
		UUID articleId = UUID.randomUUID();
		Article article = Article.builder()
			.id(articleId)
			.source(ArticleSource.NAVER)
			.title("Test Title")
			.summary("Test Summary")
			.sourceUrl("http://test.com")
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11))
			.thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(articleId, ArticleSource.NAVER, "http://test.com", "Test Title", Instant.now(),
				"Test Summary", 5L, 10L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
		ArticleResponse response = dto.getContent().get(0);
		assertThat(response.id()).isEqualTo(articleId);
		assertThat(response.title()).isEqualTo("Test Title");
		assertThat(response.summary()).isEqualTo("Test Summary");
		assertThat(response.sourceUrl()).isEqualTo("http://test.com");
	}

	@Test
	@DisplayName("getAllSources - 모든 ArticleSource enum 값을 반환")
	void shouldReturnAllArticleSources() {
		List<String> sources = articleService.getAllSources();

		assertThat(sources).isNotEmpty();
		assertThat(sources.size()).isEqualTo(ArticleSource.values().length);
	}

	@Test
	@DisplayName("fetchArticles - requestedSize가 0일 때 처리")
	void shouldHandleZeroRequestedSize() {
		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", null, null, 0
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 1))
			.thenReturn(Collections.emptyList());
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(0L);

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getSize()).isEqualTo(0);
		assertThat(dto.getContent()).isEmpty();
	}

	@Test
	@DisplayName("fetchArticles - currentUserId가 null일 때 viewedByMe는 false")
	void shouldSetViewedByMeFalseWhenUserIdIsNull() {
		UUID articleId = UUID.randomUUID();
		Article article = Article.builder()
			.id(articleId)
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11))
			.thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(articleId, ArticleSource.NAVER, "url", "Title", Instant.now(), "Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, null);

		assertThat(dto.getContent().get(0).viewedByMe()).isFalse();
	}

	@Test
	@DisplayName("fetchArticles - 마지막 기사의 commentCount가 정확히 설정되는지 확인 (hasNext=true)")
	void shouldSetLastArticleCommentCountCorrectlyWhenHasNext() {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();

		Article a1 = Article.builder().id(id1).source(ArticleSource.NAVER).publishDate(Instant.now()).build();
		Article a2 = Article.builder()
			.id(id2)
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now().minusSeconds(1))
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"COMMENT_COUNT", "DESC", null, null, 1
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 2))
			.thenReturn(List.of(a1, a2));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(2L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(id1, ArticleSource.NAVER, "url", "A1", Instant.now(), "S1", 10L, 0L, false),
			new ArticleResponse(id2, ArticleSource.NAVER, "url", "A2", Instant.now().minusSeconds(1), "S2", 5L, 0L,
				false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.isHasNext()).isTrue();
		assertThat(dto.getNextCursor()).isEqualTo("5");
	}

	@Test
	@DisplayName("Service - restoreArticles에서 키워드 매칭으로 ArticleInterest 생성 확인")
	void shouldCreateArticleInterestsBasedOnKeywordMatching() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 1, 0, 0);

		Interest interest1 = Interest.builder().id(UUID.randomUUID()).name("경제").build();
		Interest interest2 = Interest.builder().id(UUID.randomUUID()).name("기술").build();

		InterestKeyword keyword1 = InterestKeyword.builder()
			.id(UUID.randomUUID())
			.interest(interest1)
			.name("삼성전자")
			.build();

		InterestKeyword keyword2 = InterestKeyword.builder()
			.id(UUID.randomUUID())
			.interest(interest2)
			.name("AI")
			.build();

		ArticleSaveDto dto1 = ArticleSaveDto.builder()
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/1")
			.title("삼성전자 실적 발표")
			.summary("삼성전자가...")
			.publishDate(Instant.now())
			.build();

		when(s3BinaryStorage.getBackupArticles(from)).thenReturn(List.of(dto1));
		when(interestRepository.findAll()).thenReturn(List.of(interest1, interest2));
		when(interestKeywordRepository.findByInterestIdIn(anyList()))
			.thenReturn(List.of(keyword1, keyword2));
		when(articleRepository.findBySourceInAndSourceUrlIn(anyList(), anyList()))
			.thenReturn(Collections.emptyList());

		List<ArticleRestoreResult> results = articleService.restoreArticles(from, to);

		verify(articleInterestRepository).saveAll(argThat(list -> {
			List<ArticleInterest> interests = (List<ArticleInterest>)list;
			return !interests.isEmpty();
		}));
	}

	@Test
	@DisplayName("Service - restoreArticles에서 대소문자 무관하게 키워드 매칭")
	void shouldMatchKeywordsCaseInsensitively() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 1, 0, 0);

		Interest interest = Interest.builder().id(UUID.randomUUID()).name("IT").build();
		InterestKeyword keyword = InterestKeyword.builder()
			.id(UUID.randomUUID())
			.interest(interest)
			.name("Apple")
			.build();

		ArticleSaveDto dto = ArticleSaveDto.builder()
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/1")
			.title("APPLE 신제품 출시")
			.summary("apple watch...")
			.publishDate(Instant.now())
			.build();

		when(s3BinaryStorage.getBackupArticles(from)).thenReturn(List.of(dto));
		when(interestRepository.findAll()).thenReturn(List.of(interest));
		when(interestKeywordRepository.findByInterestIdIn(anyList())).thenReturn(List.of(keyword));
		when(articleRepository.findBySourceInAndSourceUrlIn(anyList(), anyList()))
			.thenReturn(Collections.emptyList());

		articleService.restoreArticles(from, to);

		verify(articleInterestRepository).saveAll(argThat(list -> {
			List<ArticleInterest> interests = (List<ArticleInterest>)list;
			return interests.size() > 0;
		}));
	}

	@Test
	@DisplayName("Service - restoreArticles에서 title이 null일 때 처리")
	void shouldHandleNullTitleDuringRestore() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 1, 0, 0);

		Interest interest = Interest.builder().id(UUID.randomUUID()).name("IT").build();
		InterestKeyword keyword = InterestKeyword.builder()
			.id(UUID.randomUUID())
			.interest(interest)
			.name("테스트")
			.build();

		ArticleSaveDto dto = ArticleSaveDto.builder()
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/1")
			.title(null)
			.summary("테스트 내용")
			.publishDate(Instant.now())
			.build();

		when(s3BinaryStorage.getBackupArticles(from)).thenReturn(List.of(dto));
		when(interestRepository.findAll()).thenReturn(List.of(interest));
		when(interestKeywordRepository.findByInterestIdIn(anyList())).thenReturn(List.of(keyword));
		when(articleRepository.findBySourceInAndSourceUrlIn(anyList(), anyList()))
			.thenReturn(Collections.emptyList());

		List<ArticleRestoreResult> results = articleService.restoreArticles(from, to);

		assertThat(results).hasSize(1);
		verify(articleRepository).saveAll(anyList());
	}

	@Test
	@DisplayName("Service - restoreArticles에서 summary가 null일 때 처리")
	void shouldHandleNullSummaryDuringRestore() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 1, 0, 0);

		Interest interest = Interest.builder().id(UUID.randomUUID()).name("IT").build();
		InterestKeyword keyword = InterestKeyword.builder()
			.id(UUID.randomUUID())
			.interest(interest)
			.name("테스트")
			.build();

		ArticleSaveDto dto = ArticleSaveDto.builder()
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/1")
			.title("테스트 제목")
			.summary(null)
			.publishDate(Instant.now())
			.build();

		when(s3BinaryStorage.getBackupArticles(from)).thenReturn(List.of(dto));
		when(interestRepository.findAll()).thenReturn(List.of(interest));
		when(interestKeywordRepository.findByInterestIdIn(anyList())).thenReturn(List.of(keyword));
		when(articleRepository.findBySourceInAndSourceUrlIn(anyList(), anyList()))
			.thenReturn(Collections.emptyList());

		List<ArticleRestoreResult> results = articleService.restoreArticles(from, to);

		assertThat(results).hasSize(1);
		verify(articleRepository).saveAll(anyList());
	}

	@Test
	@DisplayName("Service - restoreArticles 여러 관심사와 매칭")
	void shouldMatchMultipleInterests() {
		LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2025, 1, 1, 0, 0);

		Interest interest1 = Interest.builder().id(UUID.randomUUID()).name("IT").build();
		Interest interest2 = Interest.builder().id(UUID.randomUUID()).name("경제").build();

		InterestKeyword keyword1 = InterestKeyword.builder()
			.id(UUID.randomUUID())
			.interest(interest1)
			.name("삼성")
			.build();

		InterestKeyword keyword2 = InterestKeyword.builder()
			.id(UUID.randomUUID())
			.interest(interest2)
			.name("삼성")
			.build();

		ArticleSaveDto dto = ArticleSaveDto.builder()
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/1")
			.title("삼성 실적")
			.summary("내용")
			.publishDate(Instant.now())
			.build();

		when(s3BinaryStorage.getBackupArticles(from)).thenReturn(List.of(dto));
		when(interestRepository.findAll()).thenReturn(List.of(interest1, interest2));
		when(interestKeywordRepository.findByInterestIdIn(anyList()))
			.thenReturn(List.of(keyword1, keyword2));
		when(articleRepository.findBySourceInAndSourceUrlIn(anyList(), anyList()))
			.thenReturn(Collections.emptyList());

		articleService.restoreArticles(from, to);

		verify(articleInterestRepository).saveAll(argThat(list -> {
			List<ArticleInterest> interests = (List<ArticleInterest>)list;
			return interests.size() == 2;
		}));
	}

	@Test
	@DisplayName("Service - parseSortType 'VIEW_COUNT' 대문자 입력")
	void shouldMapUppercaseViewCount() {
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"VIEW_COUNT", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11))
			.thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article.getId(), ArticleSource.NAVER, "url", "Title",
				article.getPublishDate(), "Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
	}

	@Test
	@DisplayName("Service - parseSortType 'COMMENT_COUNT' 대문자 입력")
	void shouldMapUppercaseCommentCount() {
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"COMMENT_COUNT", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11))
			.thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article.getId(), ArticleSource.NAVER, "url", "Title",
				article.getPublishDate(), "Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
	}

	@Test
	@DisplayName("Service - parseSortType 'publishdate' 소문자 입력")
	void shouldMapLowercasePublishdate() {
		Article article = Article.builder()
			.id(UUID.randomUUID())
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"publishdate", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11))
			.thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(article.getId(), ArticleSource.NAVER, "url", "Title",
				article.getPublishDate(), "Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(1);
	}

	@Test
	@DisplayName("Service - fetchArticles 대량 데이터 처리")
	void shouldHandleLargeDataSet() {
		List<Article> articles = IntStream.range(0, 100)
			.mapToObj(i -> Article.builder()
				.id(UUID.randomUUID())
				.source(ArticleSource.NAVER)
				.publishDate(Instant.now().minusSeconds(i))
				.build())
			.collect(Collectors.toList());

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", null, null, 50
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 51))
			.thenReturn(articles.subList(0, 51));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(100L);
		when(articleMapper.toResponseList(anyList())).thenReturn(
			articles.subList(0, 51).stream()
				.map(a -> new ArticleResponse(a.getId(), ArticleSource.NAVER, "url",
					"Title", a.getPublishDate(), "Summary", 0L, 0L, false))
				.toList()
		);

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent()).hasSize(50);
		assertThat(dto.isHasNext()).isTrue();
	}

	@Test
	@DisplayName("Service - addArticleView 콘솔 출력 확인용")
	void shouldPrintUserIdInAddArticleView() {
		Article article = Article.builder().id(ARTICLE_ID).build();

		when(articleRepositoryCustom.findArticleById(ARTICLE_ID)).thenReturn(Optional.of(article));
		when(articleViewRepository.existsByArticleIdAndUserId(ARTICLE_ID, DUMMY_USER_ID))
			.thenReturn(true);

		articleService.addArticleView(ARTICLE_ID, DUMMY_USER_ID);

		verify(articleViewRepository, never()).save(any());
	}

	@Test
	@DisplayName("Service - softDeleteArticle 콘솔 출력 확인용")
	void shouldPrintEntityContainsInSoftDelete() {
		Article article = Article.builder().id(ARTICLE_ID).build();

		when(articleRepositoryCustom.findByIdAndDeletedAtIsNull(ARTICLE_ID))
			.thenReturn(Optional.of(article));
		when(entityManager.contains(article)).thenReturn(true);

		articleService.softDeleteArticle(ARTICLE_ID);

		verify(entityManager).contains(article);
	}

	@Test
	@DisplayName("Service - restoreArticles from과 to가 같은 날짜")
	void shouldHandleSameDateForFromAndTo() {
		LocalDateTime sameDate = LocalDateTime.of(2025, 1, 1, 0, 0);

		ArticleSaveDto dto = ArticleSaveDto.builder()
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/1")
			.title("Article")
			.summary("Summary")
			.publishDate(Instant.now())
			.build();

		when(s3BinaryStorage.getBackupArticles(sameDate)).thenReturn(List.of(dto));
		when(interestRepository.findAll()).thenReturn(Collections.emptyList());
		when(articleRepository.findBySourceInAndSourceUrlIn(anyList(), anyList()))
			.thenReturn(Collections.emptyList());

		List<ArticleRestoreResult> results = articleService.restoreArticles(sameDate, sameDate);

		assertThat(results).hasSize(1);
		verify(s3BinaryStorage, times(1)).getBackupArticles(any());
	}

	@Test
	@DisplayName("Service - fetchArticles viewedArticleIds가 비어있을 때")
	void shouldHandleEmptyViewedArticleIds() {
		UUID articleId = UUID.randomUUID();
		Article article = Article.builder()
			.id(articleId)
			.source(ArticleSource.NAVER)
			.publishDate(Instant.now())
			.build();

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		when(articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 11))
			.thenReturn(List.of(article));
		when(articleRepositoryCustom.countArticlesWithFilter(request)).thenReturn(1L);
		when(articleViewRepository.findArticleIdsViewedByUser(anyList(), any()))
			.thenReturn(Collections.emptySet());
		when(articleMapper.toResponseList(anyList())).thenReturn(List.of(
			new ArticleResponse(articleId, ArticleSource.NAVER, "url", "Title",
				Instant.now(), "Summary", 0L, 0L, false)
		));

		CursorPageResponseArticleDto dto = articleService.fetchArticles(request, DUMMY_USER_ID);

		assertThat(dto.getContent().get(0).viewedByMe()).isFalse();
	}
}
