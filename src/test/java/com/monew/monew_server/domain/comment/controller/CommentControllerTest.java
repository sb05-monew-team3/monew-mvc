package com.monew.monew_server.domain.comment.controller;

import com.monew.monew_server.domain.comment.dto.CommentDto;
import com.monew.monew_server.domain.comment.dto.CommentLikeDto;
import com.monew.monew_server.domain.comment.dto.CursorPageResponse;
import com.monew.monew_server.domain.comment.service.CommentLikeService;
import com.monew.monew_server.domain.comment.service.CommentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;



@WebMvcTest(CommentController.class)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private CommentService commentService;
    @MockBean
    private CommentLikeService commentLikeService;


    // 댓글 생성 테스트 (댓글 생성 성공)
    @Test // Junit 에게 이건 테스트라고 알려주는것
    @DisplayName("POST /api/comments - 댓글 생성 성공") // 테스트 결과를 한글로 표시
    void createComment_Success() throws Exception { // MockMvc가 던질수 있는 예외 처리
        // Given 테스트 데이터 준비
        UUID articleId = UUID.randomUUID(); // 테스트용 랜덤뉴스기사생성
        UUID userId = UUID.randomUUID(); // 테스트용 랜덤유저생성
        String content = "댓글 테스트 123";

        // Mock 응답 데이터 (Service가 반환할 값)
        CommentDto mockResponse = CommentDto.builder()
                .id(UUID.randomUUID())
                .articleId(articleId)
                .userId(userId)
                .userNickname("테스터")
                .content(content)
                .likeCount(0L)  // ← 수정됨! 0L 추가
                .likedByMe(false)
                .createdAt(Instant.now())
                .build();

        // Service Mock 설정: "createComment가 호출되면 mockResponse를 반환해"
        given(commentService.createComment(any()))
                .willReturn(mockResponse);

        // When & Then: HTTP POST 요청 + 검증
        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "articleId": "%s",
                                    "userId": "%s",
                                    "content": "%s"
                                }
                                """.formatted(articleId, userId,
                                content)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.content").value(content))

                .andExpect(jsonPath("$.userNickname").value("테스터"));
    }

    @Test
    @DisplayName("PATCH /api/comments/{commentId} - 댓글 수정 성공")
    void updateComment_Success() throws Exception {
        // Given : 테스트 데이터 준비
        UUID commentId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String updatedContent = "수정된 댓글 내용입니다.";

        // Mock 응답 데이터 (수정 후 반환될 댓글 응답)
        CommentDto mockResponce = CommentDto.builder()
                .id(commentId)
                .articleId(articleId)
                .userId(userId)
                .userNickname("테스터")
                .createdAt(Instant.now())
                .likeCount(0L)
                .likedByMe(false)
                .content(updatedContent) // 수정할 메세지는 정해졌으니 할당됨
                .build();

        // Service Mock 설정
        given(commentService.updateComment(eq(commentId), eq(userId),
                any())).willReturn(mockResponce);

        // When Then : HTTP PATCH 요청 + 검증 과정
// When & Then: HTTP PATCH 요청 + 검증
        mockMvc.perform(patch("/api/comments/{commentId}",
                        commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Monew-Request-User-ID",
                                userId.toString())
                        .content("""
                                {
                                    "content": "%s"
                                }
                                """.formatted(updatedContent)))
                .andDo(print())
                .andExpect(status().isOk())

                .andExpect(jsonPath("$.id").value(commentId.toString()))

                .andExpect(jsonPath("$.content").value(updatedContent));
    }


    /**
     * GET /api/comments 테스트
     *
     * <WebMvcTest란?>
     * - Controller 레이어만 테스트하는 슬라이스 테스트
     * - Service는 @MockBean으로 가짜 객체 사용
     * - 실제 DB 연결 없이 컨트롤러 로직만 검증
     *
     * <given-when-then 패턴>
     * - Given: 테스트 데이터와 Mock 설정
     * - When: 실제 HTTP 요청 실행
     * - Then: 응답 검증
     *
     * <jsonPath란?>
     * - JSON 응답의 특정 필드 값을 검증하는 도구
     * - $.content[0].content: content 배열의 첫 번째 요소의 content 필드
     * - $.hasNext: 최상위 hasNext 필드
     */
    @Test
    @DisplayName("GET /api/comments - 댓글 목록 조회 성공 (커서 페이지네이션)")
    void getComments_Success() throws Exception {
        // Given: 테스트 데이터 준비
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommentDto comment1 = CommentDto.builder()
                .id(UUID.randomUUID())
                .articleId(articleId)
                .userId(userId)
                .userNickname("테스터1")
                .content("첫번째 댓글입니다")
                .likeCount(5L)
                .likedByMe(false)
                .createdAt(Instant.now())
                .build();

        // CursorPageResponse 생성 (Swagger 명세에 맞춤)
        CursorPageResponse<CommentDto> mockResponse = CursorPageResponse.<CommentDto>builder()
                .content(List.of(comment1))
                .nextCursor(null)  // 마지막 페이지
                .nextAfter(null)
                .size(1)
                .totalElements(1L)
                .hasNext(false)  // 다음 페이지 없음
                .build();

        // Service Mock 설정: "getComments가 호출되면 mockResponse 반환"
        // 파라미터: articleId, orderBy, direction, cursor, after, limit, userId (총 7개)
        given(commentService.getComments(
                any(UUID.class),    // articleId
                any(String.class),  // orderBy
                any(String.class),  // direction
                any(),              // cursor (nullable)
                any(),              // after (nullable)
                anyInt(),           // limit
                any(UUID.class)     // userId
        )).willReturn(mockResponse);

        // When & Then: HTTP 요청 후 응답 검증
        mockMvc.perform(get("/api/comments")
                        .param("articleId", articleId.toString())
                        .param("orderBy", "createdAt")
                        .param("direction", "ASC")
                        .param("limit", "50")
                        .header("Monew-Request-User-ID", userId.toString()))
                .andDo(print())
                .andExpect(status().isOk())
                // CursorPageResponse 구조 검증
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].content").value("첫번째 댓글입니다"))
                .andExpect(jsonPath("$.content[0].likeCount").value(5))
                .andExpect(jsonPath("$.content[0].likedByMe").value(false))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("DELETE /api/comments/{commentId} - 논리 삭제 성공")
    void deleteComment_Success() throws Exception {
        // Given: 테스트 데이터 준비
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // When & Then: HTTP DELETE 요청 + 검증
        mockMvc.perform(delete("/api/comments/{commentId}", commentId)
                        .header("Monew-Request-User-ID", userId.toString()))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/comments/{commentId}/hard - 물리 삭제 성공")
    void hardDeleteComment_Success() throws Exception {
        // Given: 테스트 데이터 준비
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // When & Then: HTTP DELETE 요청 + 검증
        mockMvc.perform(delete("/api/comments/{commentId}/hard", commentId)
                        .header("Monew-Request-User-ID", userId.toString()))
                .andDo(print())
                .andExpect(status().isNoContent());
    }


    // 좋아요 테스트
    @Test
    @DisplayName("POST /api/comments/{commentId}/comment-likes - 좋아요 추가 성공")
    void addLike_Success() throws Exception {
        // Given: 테스트 데이터 준비
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID likeId = UUID.randomUUID();

        // Mock 응답 데이터
        CommentLikeDto mockResponse = CommentLikeDto.builder()
                .id(likeId)
                .likedBy(userId)
                .createdAt(Instant.now())
                .commentId(commentId)
                .articleId(articleId)
                .commentUserId(userId)
                .commentUserNickname("댓글작성자")
                .commentContent("좋은 글이네요")
                .commentLikeCount(1L)
                .commentCreatedAt(Instant.now())
                .build();

        // Service Mock 설정
        given(commentLikeService.addLike(eq(commentId), eq(userId)))
                .willReturn(mockResponse);

        // When & Then: HTTP POST 요청 + 검증
        mockMvc.perform(post("/api/comments/{commentId}/comment-likes", commentId)
                        .header("Monew-Request-User-ID", userId.toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(likeId.toString()))
                .andExpect(jsonPath("$.likedBy").value(userId.toString()))
                .andExpect(jsonPath("$.commentId").value(commentId.toString()))
                .andExpect(jsonPath("$.commentLikeCount").value(1))
                .andExpect(jsonPath("$.commentContent").value("좋은 글이네요"));
    }

    @Test
    @DisplayName("DELETE /api/comments/{commentId}/comment-likes - 좋아요 취소 성공")
    void removeLike_Success() throws Exception {
        // Given: 테스트 데이터 준비
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // When & Then: HTTP DELETE 요청 + 검증
        mockMvc.perform(delete("/api/comments/{commentId}/comment-likes", commentId)
                        .header("Monew-Request-User-ID", userId.toString()))
                .andDo(print())
                .andExpect(status().isOk());
    }

}
