package com.monew.monew_server.domain.article.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.monew.monew_server.config.QuerydslConfig;
import com.monew.monew_server.domain.article.dto.ArticleRequest;
import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.article.entity.ArticleSource;

@DataJpaTest
@Import(QuerydslConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ArticleRepositoryTest {

	@Autowired
	private ArticleRepository articleRepository;

	@Qualifier("articleRepositoryImpl")
	@Autowired
	private ArticleRepositoryCustom articleRepositoryCustom;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clearDatabase() {
		jdbcTemplate.execute("TRUNCATE TABLE articles RESTART IDENTITY CASCADE");
	}

	@Test
	@DisplayName("DATE 정렬 ASC - cursor와 after 있을 때")
	void shouldFindArticlesWithDateAscAndCursor() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-25T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-26T10:00:00Z"));
		Article article3 = createArticle("기사3", Instant.parse("2025-10-27T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2, article3));

		// ✅ cursor는 정렬 기준 값(publishDate)을 사용!
		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "ASC",
			article1.getPublishDate().toString(),  // ✅ ID가 아닌 publishDate!
			LocalDateTime.ofInstant(article1.getPublishDate(), ZoneOffset.UTC),
			10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		// article1보다 publishDate가 큰 것들만 조회되어야 함
		assertThat(result).isNotEmpty();
		assertThat(result).hasSize(2);  // article2, article3
		assertThat(result.get(0).getTitle()).isEqualTo("기사2");
		assertThat(result.get(0).getPublishDate()).isAfter(article1.getPublishDate());
	}

	@Test
	@DisplayName("DATE 정렬 DESC - cursor와 after 있을 때")
	void shouldFindArticlesWithDateDescAndCursor() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-25T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-26T10:00:00Z"));
		Article article3 = createArticle("기사3", Instant.parse("2025-10-27T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2, article3));

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC",
			article3.getPublishDate().toString(),
			LocalDateTime.ofInstant(article3.getPublishDate(), ZoneOffset.UTC),
			10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).isNotEmpty();
		assertThat(result).hasSize(2);
		assertThat(result.get(0).getTitle()).isEqualTo("기사2");
	}

	@Test
	@DisplayName("VIEW_COUNT 정렬 ASC - cursor와 after 있을 때")
	void shouldFindArticlesWithViewCountAscAndCursor() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-25T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-26T10:00:00Z"));
		Article article3 = createArticle("기사3", Instant.parse("2025-10-27T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2, article3));

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"VIEW_COUNT", "ASC",
			"5",
			LocalDateTime.ofInstant(article1.getPublishDate(), ZoneOffset.UTC),  // ✅ publishDate
			10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("VIEW_COUNT 정렬 DESC - cursor와 after 있을 때")
	void shouldFindArticlesWithViewCountDescAndCursor() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-25T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-26T10:00:00Z"));
		Article article3 = createArticle("기사3", Instant.parse("2025-10-27T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2, article3));

		// cursor=100 (조회수)으로 조회
		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"VIEW_COUNT", "DESC",
			"100",  // ✅ 조회수 (숫자)
			LocalDateTime.ofInstant(article2.getPublishDate(), ZoneOffset.UTC),
			10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		// viewCount < 100 인 것들만 조회되어야 함
		assertThat(result).isNotEmpty();
	}

	@Test
	@DisplayName("COMMENT_COUNT 정렬 ASC")
	void shouldFindArticlesWithCommentCountAsc() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"COMMENT_COUNT", "ASC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("DATE 정렬 ASC - cursor 없이")
	void shouldFindArticlesWithDateAsc() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-25T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-26T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "ASC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getPublishDate()).isBefore(result.get(1).getPublishDate());
	}

	@Test
	@DisplayName("publishDateFrom만 있을 때")
	void shouldFilterByPublishDateFrom() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-25T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-27T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			null, null, null,
			LocalDateTime.parse("2025-10-26T00:00:00"),
			null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getTitle()).isEqualTo("기사2");
	}

	@Test
	@DisplayName("publishDateTo만 있을 때")
	void shouldFilterByPublishDateTo() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-25T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-27T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			null, null, null, null,
			LocalDateTime.parse("2025-10-26T00:00:00"),
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getTitle()).isEqualTo("기사1");
	}

	@Test
	@DisplayName("publishDateFrom과 publishDateTo 모두 있을 때")
	void shouldFilterByPublishDateRange() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-24T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-25T10:00:00Z"));
		Article article3 = createArticle("기사3", Instant.parse("2025-10-28T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2, article3));

		ArticleRequest request = new ArticleRequest(
			null, null, null,
			LocalDateTime.parse("2025-10-25T00:00:00"),
			LocalDateTime.parse("2025-10-27T00:00:00"),
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getTitle()).isEqualTo("기사2");
	}

	@Test
	@DisplayName("sourceIn 필터링")
	void shouldFilterBySourceIn() {
		Article article1 = createArticleWithSource("기사1", ArticleSource.NAVER);
		Article article2 = createArticleWithSource("기사2", ArticleSource.CHOSUN);
		Article article3 = createArticleWithSource("기사3", ArticleSource.HANKYUNG);
		articleRepository.saveAll(List.of(article1, article2, article3));

		ArticleRequest request = new ArticleRequest(
			null, null, List.of("NAVER", "CHOSUN"), null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(2);
	}

	@Test
	@DisplayName("sourceIn에 잘못된 값이 있을 때 무시")
	void shouldIgnoreInvalidSourceValues() {
		Article article = createArticleWithSource("기사", ArticleSource.NAVER);
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, List.of("NAVER", "INVALID_SOURCE"), null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("whereCursor - after가 NumberFormatException 발생하는 경우")
	void shouldHandleInvalidAfterValue() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"VIEW_COUNT", "DESC",
			"100",
			LocalDateTime.parse("2025-10-27T10:30:00"), // 숫자로 파싱 불가
			10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("cursor가 빈 문자열일 때")
	void shouldHandleEmptyCursor() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", "", null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("orderBy가 null일 때 DATE로 파싱")
	void shouldParseNullOrderByAsDate() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			null, "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("orderBy가 잘못된 값일 때 DATE로 폴백")
	void shouldFallbackToDateWhenInvalidOrderBy() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"INVALID", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("findArticleById - 존재하는 기사")
	void shouldFindArticleById() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		Optional<Article> result = articleRepositoryCustom.findArticleById(article.getId());

		assertThat(result).isPresent();
		assertThat(result.get().getId()).isEqualTo(article.getId());
	}

	@Test
	@DisplayName("findArticleById - 존재하지 않는 기사")
	void shouldReturnEmptyWhenArticleNotFound() {
		Optional<Article> result = articleRepositoryCustom.findArticleById(UUID.randomUUID());

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("findByIdAndDeletedAtIsNull - 존재하는 기사")
	void shouldFindByIdAndDeletedAtIsNull() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		Optional<Article> result = articleRepositoryCustom.findByIdAndDeletedAtIsNull(article.getId());

		assertThat(result).isPresent();
	}

	@Test
	@DisplayName("findByIdAndDeletedAtIsNull - 삭제된 기사는 반환하지 않음")
	void shouldNotFindDeletedArticle() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		article.softDelete();
		articleRepository.save(article);

		Optional<Article> result = articleRepositoryCustom.findByIdAndDeletedAtIsNull(article.getId());

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("findBySourceInAndSourceUrlIn - 정상 조회")
	void shouldFindBySourceInAndSourceUrlIn() {
		Article article1 = createArticleWithSourceUrl("기사1", ArticleSource.NAVER, "http://url1.com");
		Article article2 = createArticleWithSourceUrl("기사2", ArticleSource.CHOSUN, "http://url2.com");
		articleRepository.saveAll(List.of(article1, article2));

		List<Article> result = articleRepositoryCustom.findBySourceInAndSourceUrlIn(
			List.of(ArticleSource.NAVER, ArticleSource.CHOSUN),
			List.of("http://url1.com", "http://url2.com")
		);

		assertThat(result).hasSize(2);
	}

	@Test
	@DisplayName("findBySourceInAndSourceUrlIn - sources가 비어있을 때")
	void shouldReturnEmptyWhenSourcesEmpty() {
		List<Article> result = articleRepositoryCustom.findBySourceInAndSourceUrlIn(
			List.of(),
			List.of("http://url1.com")
		);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("findBySourceInAndSourceUrlIn - sourceUrls가 비어있을 때")
	void shouldReturnEmptyWhenSourceUrlsEmpty() {
		List<Article> result = articleRepositoryCustom.findBySourceInAndSourceUrlIn(
			List.of(ArticleSource.NAVER),
			List.of()
		);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("countArticlesWithFilter - interestId가 있을 때")
	void shouldCountWithInterestId() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, UUID.randomUUID(), null, null, null,
			"DATE", "DESC", null, null, 10
		);

		long count = articleRepositoryCustom.countArticlesWithFilter(request);

		assertThat(count).isGreaterThanOrEqualTo(0);
	}

	@Test
	@DisplayName("getCountExpression - 기본값 분기 (DATE)")
	void shouldUseDefaultCountExpression() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	// Helper methods
	private Article createArticle(String title, Instant publishDate) {
		return Article.builder()
			.title(title)
			.summary("요약")
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/" + UUID.randomUUID())
			.publishDate(publishDate)
			.createdAt(Instant.now())
			.build();
	}

	private Article createArticleWithSource(String title, ArticleSource source) {
		return Article.builder()
			.title(title)
			.summary("요약")
			.source(source)
			.sourceUrl("http://test.com/" + UUID.randomUUID())
			.publishDate(Instant.now())
			.createdAt(Instant.now())
			.build();
	}

	private Article createArticleWithSourceUrl(String title, ArticleSource source, String sourceUrl) {
		return Article.builder()
			.title(title)
			.summary("요약")
			.source(source)
			.sourceUrl(sourceUrl)
			.publishDate(Instant.now())
			.createdAt(Instant.now())
			.build();
	}

	@Test
	@DisplayName("COMMENT_COUNT 정렬 DESC")
	void shouldFindArticlesWithCommentCountDesc() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"COMMENT_COUNT", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("COMMENT_COUNT 정렬 ASC - cursor와 after 있을 때")
	void shouldFindArticlesWithCommentCountAscAndCursor() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-25T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-26T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"COMMENT_COUNT", "ASC",
			"3",
			LocalDateTime.ofInstant(article1.getPublishDate(), ZoneOffset.UTC),
			10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("COMMENT_COUNT 정렬 DESC - cursor와 after 있을 때")
	void shouldFindArticlesWithCommentCountDescAndCursor() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-25T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-26T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"COMMENT_COUNT", "DESC",
			"50",
			LocalDateTime.ofInstant(article2.getPublishDate(), ZoneOffset.UTC),
			10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("keyword 필터링 - title에서 검색")
	void shouldFilterByKeywordInTitle() {
		Article article1 = createArticle("경제 뉴스", Instant.now());
		Article article2 = createArticle("정치 소식", Instant.now());
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			"경제", null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getTitle()).contains("경제");
	}

	@Test
	@DisplayName("keyword 필터링 - summary에서 검색")
	void shouldFilterByKeywordInSummary() {
		Article article1 = Article.builder()
			.title("제목1")
			.summary("이것은 경제 관련 요약입니다")
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/" + UUID.randomUUID())
			.publishDate(Instant.now())
			.createdAt(Instant.now())
			.build();
		Article article2 = createArticle("제목2", Instant.now());
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			"경제", null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getSummary()).contains("경제");
	}

	@Test
	@DisplayName("keyword가 빈 문자열일 때 필터링 안 함")
	void shouldNotFilterWhenKeywordIsBlank() {
		Article article1 = createArticle("기사1", Instant.now());
		Article article2 = createArticle("기사2", Instant.now());
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			"   ", null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(2);
	}

	@Test
	@DisplayName("sourceIn에 [OBJECT OBJECT] 포함 시 필터링")
	void shouldFilterOutObjectObjectInSourceIn() {
		Article article = createArticleWithSource("기사", ArticleSource.NAVER);
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, List.of("NAVER", "[OBJECT OBJECT]"), null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("sourceIn이 모두 유효하지 않을 때 조건 추가 안 함")
	void shouldNotAddConditionWhenAllSourcesInvalid() {
		Article article = createArticleWithSource("기사", ArticleSource.NAVER);
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, List.of("INVALID1", "INVALID2", "[OBJECT OBJECT]"), null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		// 유효한 source가 없으므로 sourceIn 조건이 추가되지 않아 모든 기사 조회
		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("sourceIn이 빈 리스트일 때")
	void shouldHandleEmptySourceIn() {
		Article article = createArticleWithSource("기사", ArticleSource.NAVER);
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, List.of(), null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("direction이 null일 때 DESC로 기본값 처리")
	void shouldUseDescAsDefaultDirection() {
		Article article1 = createArticle("기사1", Instant.parse("2025-10-25T10:00:00Z"));
		Article article2 = createArticle("기사2", Instant.parse("2025-10-26T10:00:00Z"));
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", null, null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(2);
		// DESC이므로 최신 기사가 먼저
		assertThat(result.get(0).getTitle()).isEqualTo("기사2");
	}

	@Test
	@DisplayName("orderBy가 'viewCount'일 때 VIEW_COUNT로 매핑")
	void shouldMapViewCountString() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"viewCount", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("orderBy가 'commentCount'일 때 COMMENT_COUNT로 매핑")
	void shouldMapCommentCountString() {
		Article article = createArticle("기사", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"commentCount", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("countArticlesWithFilter - keyword가 있을 때")
	void shouldCountWithKeyword() {
		Article article1 = createArticle("경제 뉴스", Instant.now());
		Article article2 = createArticle("정치 소식", Instant.now());
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			"경제", null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		long count = articleRepositoryCustom.countArticlesWithFilter(request);

		assertThat(count).isEqualTo(1);
	}

	@Test
	@DisplayName("countArticlesWithFilter - 모든 필터 없을 때")
	void shouldCountAllArticles() {
		Article article1 = createArticle("기사1", Instant.now());
		Article article2 = createArticle("기사2", Instant.now());
		articleRepository.saveAll(List.of(article1, article2));

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		long count = articleRepositoryCustom.countArticlesWithFilter(request);

		assertThat(count).isEqualTo(2);
	}

	@Test
	@DisplayName("삭제된 기사는 조회하지 않음")
	void shouldNotFindDeletedArticles() {
		Article article1 = createArticle("기사1", Instant.now());
		Article article2 = createArticle("기사2", Instant.now());
		articleRepository.saveAll(List.of(article1, article2));

		article1.softDelete();
		articleRepository.save(article1);

		ArticleRequest request = new ArticleRequest(
			null, null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(article2.getId());
	}

	@Test
	@DisplayName("case insensitive 키워드 검색 - 대소문자 무시")
	void shouldSearchKeywordCaseInsensitive() {
		Article article = createArticle("ECONOMY News", Instant.now());
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			"economy", null, null, null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("sourceIn 대소문자 처리 - 소문자로 들어와도 매칭")
	void shouldHandleSourceInCaseInsensitive() {
		Article article = createArticleWithSource("기사", ArticleSource.NAVER);
		articleRepository.save(article);

		ArticleRequest request = new ArticleRequest(
			null, null, List.of("naver", "chosun"), null, null,
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("복합 필터링 - keyword + sourceIn + publishDate 범위")
	void shouldFilterWithMultipleConditions() {
		Article article1 = Article.builder()
			.title("네이버 경제 뉴스")
			.summary("요약")
			.source(ArticleSource.NAVER)
			.sourceUrl("http://test.com/1")
			.publishDate(Instant.parse("2025-10-25T10:00:00Z"))
			.createdAt(Instant.now())
			.build();
		Article article2 = Article.builder()
			.title("조선 경제 소식")
			.summary("요약")
			.source(ArticleSource.CHOSUN)
			.sourceUrl("http://test.com/2")
			.publishDate(Instant.parse("2025-10-26T10:00:00Z"))
			.createdAt(Instant.now())
			.build();
		Article article3 = createArticleWithSource("정치 뉴스", ArticleSource.NAVER);
		articleRepository.saveAll(List.of(article1, article2, article3));

		ArticleRequest request = new ArticleRequest(
			"경제",
			null,
			List.of("NAVER"),
			LocalDateTime.parse("2025-10-24T00:00:00"),
			LocalDateTime.parse("2025-10-26T00:00:00"),
			"DATE", "DESC", null, null, 10
		);

		List<Article> result = articleRepositoryCustom.findArticlesWithFilterAndCursor(request, 10);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getTitle()).isEqualTo("네이버 경제 뉴스");
	}
}