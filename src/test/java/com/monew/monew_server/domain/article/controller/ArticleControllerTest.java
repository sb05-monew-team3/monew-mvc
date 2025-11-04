package com.monew.monew_server.domain.article.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.monew.monew_server.domain.article.dto.ArticleRequest;
import com.monew.monew_server.domain.article.dto.ArticleResponse;
import com.monew.monew_server.domain.article.dto.ArticleRestoreResult;
import com.monew.monew_server.domain.article.dto.CursorPageResponseArticleDto;
import com.monew.monew_server.domain.article.entity.ArticleSource;
import com.monew.monew_server.domain.article.service.ArticleService;

@WebMvcTest(
	controllers = ArticleController.class,
	excludeAutoConfiguration = {
		JpaRepositoriesAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class,
		SecurityAutoConfiguration.class
	}
)
@AutoConfigureMockMvc(addFilters = false)
class ArticleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ArticleService articleService;

	private final UUID ARTICLE_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private final UUID ARTICLE_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	@DisplayName("전체 뉴스 목록 조회 성공")
	void shouldReturnArticles_whenGetArticles() throws Exception {
		ArticleResponse article1 = new ArticleResponse(
			ARTICLE_ID_1, ArticleSource.NAVER, "http://naver.com/1", "기사 제목 1", Instant.now(), "기사 요약 1", 10L, 2L, true
		);
		ArticleResponse article2 = new ArticleResponse(
			ARTICLE_ID_2, ArticleSource.CHOSUN, "http://naver.com/2", "기사 제목 2", Instant.now(), "기사 요약 2", 5L, 0L, false
		);

		CursorPageResponseArticleDto mockResponse = CursorPageResponseArticleDto.builder()
			.content(List.of(article1, article2))
			.hasNext(false)
			.size(10)
			.totalElements(2)
			.build();

		when(articleService.fetchArticles(any(ArticleRequest.class), any(UUID.class)))
			.thenReturn(mockResponse);

		mockMvc.perform(get("/api/articles")
				.param("keyword", "")
				.param("limit", "10")
				.param("orderBy", "DATE")
				.param("cursor", "")
				.param("direction", "DESC")
				.header("Monew-Request-User-ID", ARTICLE_ID_1.toString())
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].title").value("기사 제목 1"))
			.andExpect(jsonPath("$.content[1].sourceUrl").value("http://naver.com/2"));
	}

	@Test
	@DisplayName("검색어로 기사 제목/요약 필터링 테스트")
	void shouldFilterArticlesByKeyword() throws Exception {
		UUID articleId = UUID.fromString("00000000-0000-0000-0000-000000000004");
		ArticleResponse filteredArticle = new ArticleResponse(
			articleId, ArticleSource.NAVER, "http://naver.com/1", "삼성전자 실적 상승", Instant.now(), "경제 뉴스", 3L, 1L, false
		);

		CursorPageResponseArticleDto mockResponse = CursorPageResponseArticleDto.builder()
			.content(List.of(filteredArticle))
			.hasNext(false)
			.size(10)
			.totalElements(1)
			.build();

		when(articleService.fetchArticles(any(ArticleRequest.class), any(UUID.class)))
			.thenReturn(mockResponse);

		mockMvc.perform(get("/api/articles")
				.param("keyword", "삼성전자")
				.param("limit", "10")
				.param("orderBy", "DATE")
				.param("direction", "DESC")
				.header("Monew-Request-User-ID", ARTICLE_ID_1.toString())
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].title").value("삼성전자 실적 상승"));
	}

	@Test
	@DisplayName("단일 기사 조회 성공")
	void shouldReturnSingleArticle_whenGetArticleById() throws Exception {
		ArticleResponse article = new ArticleResponse(
			ARTICLE_ID_1, ArticleSource.NAVER, "http://naver.com/1", "단일 기사", Instant.now(), "요약",
			5L,
			10L,
			true
		);
		when(articleService.getArticleById(any(UUID.class), any(UUID.class)))
			.thenReturn(article);

		mockMvc.perform(get("/api/articles/" + ARTICLE_ID_1)
				.header("Monew-Request-User-ID", ARTICLE_ID_2.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("단일 기사"))
			.andExpect(jsonPath("$.commentCount").value(5))
			.andExpect(jsonPath("$.viewCount").value(10));
	}

	@Test
	@DisplayName("기사 조회수 추가 API 호출 성공")
	void shouldAddArticleView() throws Exception {
		doNothing().when(articleService).addArticleView(any(UUID.class), any(UUID.class));

		mockMvc.perform(post("/api/articles/" + ARTICLE_ID_1 + "/article-views")
				.header("Monew-Request-User-ID", ARTICLE_ID_2.toString()))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("기사 소스 목록 조회 성공 (String List 형식)")
	void shouldReturnArticleSourcesAsStringList() throws Exception {
		List<String> mockSources = List.of("NAVER", "HANKYUNG");

		when(articleService.getAllSources()).thenReturn(mockSources);

		mockMvc.perform(get("/api/articles/sources"))
			.andExpect(status().isOk())

			.andExpect(jsonPath("$[0]").value("NAVER"))
			.andExpect(jsonPath("$[1]").value("HANKYUNG"));
	}

	@Test
	@DisplayName("기사 소프트 삭제 성공")
	void shouldSoftDeleteArticle() throws Exception {
		doNothing().when(articleService).softDeleteArticle(ARTICLE_ID_1);

		mockMvc.perform(delete("/api/articles/" + ARTICLE_ID_1))
			.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("기사 하드 삭제 성공")
	void shouldHardDeleteArticle() throws Exception {
		doNothing().when(articleService).hardDeleteArticle(ARTICLE_ID_2);

		mockMvc.perform(delete("/api/articles/" + ARTICLE_ID_2 + "/hard"))
			.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("기사 복구 API 성공")
	void shouldRestoreArticles() throws Exception {
		List<ArticleRestoreResult> results = List.of(
			new ArticleRestoreResult(LocalDateTime.now(), List.of(ARTICLE_ID_1), 1)
		);

		when(articleService.restoreArticles(any(LocalDateTime.class), any(LocalDateTime.class)))
			.thenReturn(results);

		mockMvc.perform(get("/api/articles/restore")
				.param("from", "2025-01-01T00:00:00")
				.param("to", "2025-01-02T00:00:00"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].restoredArticleCount").value(1))  // 필드명 수정
			.andExpect(jsonPath("$[0].restoredArticleIds[0]").value(ARTICLE_ID_1.toString()))  // 추가 검증
			.andExpect(jsonPath("$[0].restoreDate").exists());  // 날짜 존재 확인
	}

	@Test
	@DisplayName("잘못된 UUID 헤더가 전달된 경우에도 정상 응답")
	void shouldHandleInvalidUUIDHeaderGracefully() throws Exception {
		CursorPageResponseArticleDto mockResponse = CursorPageResponseArticleDto.builder()
			.content(List.of())
			.hasNext(false)
			.size(10)
			.totalElements(0)
			.build();

		when(articleService.fetchArticles(any(ArticleRequest.class), any(UUID.class)))
			.thenReturn(mockResponse);

		mockMvc.perform(get("/api/articles")
				.header("Monew-Request-User-ID", "INVALID-UUID"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("User-ID 헤더가 null인 경우 정상 처리")
	void shouldHandleNullUserIdHeader() throws Exception {
		CursorPageResponseArticleDto mockResponse = CursorPageResponseArticleDto.builder()
			.content(List.of())
			.hasNext(false)
			.size(10)
			.totalElements(0)
			.build();

		when(articleService.fetchArticles(any(ArticleRequest.class), isNull()))
			.thenReturn(mockResponse);

		mockMvc.perform(get("/api/articles")
				.param("limit", "10"))
			.andExpect(status().isOk());

		verify(articleService).fetchArticles(any(ArticleRequest.class), isNull());
	}

	@Test
	@DisplayName("User-ID 헤더가 빈 문자열인 경우 정상 처리")
	void shouldHandleEmptyUserIdHeader() throws Exception {
		CursorPageResponseArticleDto mockResponse = CursorPageResponseArticleDto.builder()
			.content(List.of())
			.hasNext(false)
			.size(10)
			.totalElements(0)
			.build();

		when(articleService.fetchArticles(any(ArticleRequest.class), isNull()))
			.thenReturn(mockResponse);

		mockMvc.perform(get("/api/articles")
				.header("Monew-Request-User-ID", "")
				.param("limit", "10"))
			.andExpect(status().isOk());

		verify(articleService).fetchArticles(any(ArticleRequest.class), isNull());
	}

	@Test
	@DisplayName("User-ID 헤더가 공백만 있는 경우 정상 처리")
	void shouldHandleBlankUserIdHeader() throws Exception {
		CursorPageResponseArticleDto mockResponse = CursorPageResponseArticleDto.builder()
			.content(List.of())
			.hasNext(false)
			.size(10)
			.totalElements(0)
			.build();

		when(articleService.fetchArticles(any(ArticleRequest.class), isNull()))
			.thenReturn(mockResponse);

		mockMvc.perform(get("/api/articles")
				.header("Monew-Request-User-ID", "   ")
				.param("limit", "10"))
			.andExpect(status().isOk());

		verify(articleService).fetchArticles(any(ArticleRequest.class), isNull());
	}

	@Test
	@DisplayName("단일 기사 조회 시 User-ID 헤더 없이도 정상 처리")
	void shouldGetArticleByIdWithoutUserIdHeader() throws Exception {
		ArticleResponse article = new ArticleResponse(
			ARTICLE_ID_1, ArticleSource.NAVER, "http://naver.com/1", "단일 기사", Instant.now(), "요약", 5L, 10L, false
		);
		when(articleService.getArticleById(eq(ARTICLE_ID_1), isNull()))
			.thenReturn(article);

		mockMvc.perform(get("/api/articles/" + ARTICLE_ID_1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("단일 기사"));

		verify(articleService).getArticleById(eq(ARTICLE_ID_1), isNull());
	}

	@Test
	@DisplayName("단일 기사 조회 시 잘못된 UUID 헤더 처리")
	void shouldGetArticleByIdWithInvalidUserIdHeader() throws Exception {
		ArticleResponse article = new ArticleResponse(
			ARTICLE_ID_1, ArticleSource.NAVER, "단일 기사", "요약", Instant.now(), "NAVER", 5L, 10L, false
		);
		when(articleService.getArticleById(eq(ARTICLE_ID_1), isNull()))
			.thenReturn(article);

		mockMvc.perform(get("/api/articles/" + ARTICLE_ID_1)
				.header("Monew-Request-User-ID", "invalid-uuid"))
			.andExpect(status().isOk());

		verify(articleService).getArticleById(eq(ARTICLE_ID_1), isNull());
	}

	@Test
	@DisplayName("조회수 추가 시 User-ID 헤더 없이도 정상 처리")
	void shouldAddArticleViewWithoutUserIdHeader() throws Exception {
		doNothing().when(articleService).addArticleView(eq(ARTICLE_ID_1), isNull());

		mockMvc.perform(post("/api/articles/" + ARTICLE_ID_1 + "/article-views"))
			.andExpect(status().isOk());

		verify(articleService).addArticleView(eq(ARTICLE_ID_1), isNull());
	}

	@Test
	@DisplayName("조회수 추가 시 잘못된 UUID 헤더 처리")
	void shouldAddArticleViewWithInvalidUserIdHeader() throws Exception {
		doNothing().when(articleService).addArticleView(eq(ARTICLE_ID_1), isNull());

		mockMvc.perform(post("/api/articles/" + ARTICLE_ID_1 + "/article-views")
				.header("Monew-Request-User-ID", "not-a-uuid"))
			.andExpect(status().isOk());

		verify(articleService).addArticleView(eq(ARTICLE_ID_1), isNull());
	}

	@Test
	@DisplayName("유효한 UUID 헤더로 전체 기사 목록 조회")
	void shouldGetArticlesWithValidUserIdHeader() throws Exception {
		UUID validUserId = UUID.randomUUID();
		CursorPageResponseArticleDto mockResponse = CursorPageResponseArticleDto.builder()
			.content(List.of())
			.hasNext(false)
			.size(10)
			.totalElements(0)
			.build();

		when(articleService.fetchArticles(any(ArticleRequest.class), eq(validUserId)))
			.thenReturn(mockResponse);

		mockMvc.perform(get("/api/articles")
				.header("Monew-Request-User-ID", validUserId.toString())
				.param("limit", "10"))
			.andExpect(status().isOk());

		verify(articleService).fetchArticles(any(ArticleRequest.class), eq(validUserId));
	}

	@Test
	@DisplayName("복수 개의 복구 결과 반환")
	void shouldRestoreMultipleArticles() throws Exception {
		List<ArticleRestoreResult> results = List.of(
			new ArticleRestoreResult(LocalDateTime.now(), List.of(ARTICLE_ID_1), 1),
			new ArticleRestoreResult(LocalDateTime.now().plusHours(1), List.of(ARTICLE_ID_2), 1)
		);

		when(articleService.restoreArticles(any(LocalDateTime.class), any(LocalDateTime.class)))
			.thenReturn(results);

		mockMvc.perform(get("/api/articles/restore")
				.param("from", "2025-01-01T00:00:00")
				.param("to", "2025-01-02T00:00:00"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].restoredArticleCount").value(1))
			.andExpect(jsonPath("$[1].restoredArticleCount").value(1));
	}

	@Test
	@DisplayName("복구할 기사가 없는 경우 빈 배열 반환")
	void shouldReturnEmptyListWhenNoArticlesToRestore() throws Exception {
		when(articleService.restoreArticles(any(LocalDateTime.class), any(LocalDateTime.class)))
			.thenReturn(List.of());

		mockMvc.perform(get("/api/articles/restore")
				.param("from", "2025-01-01T00:00:00")
				.param("to", "2025-01-02T00:00:00"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}
}