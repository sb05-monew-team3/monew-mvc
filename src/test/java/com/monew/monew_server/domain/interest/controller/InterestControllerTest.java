package com.monew.monew_server.domain.interest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monew.monew_server.domain.interest.dto.CursorPageResponseInterestDto;
import com.monew.monew_server.domain.interest.dto.InterestDto;
import com.monew.monew_server.domain.interest.dto.InterestQuery;
import com.monew.monew_server.domain.interest.dto.InterestRegisterRequest;
import com.monew.monew_server.domain.interest.dto.InterestUpdateRequest;
import com.monew.monew_server.domain.interest.dto.SubscriptionDto;
import com.monew.monew_server.domain.interest.service.InterestService;
import com.monew.monew_server.exception.ErrorCode;
import com.monew.monew_server.exception.NotFoundException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InterestController.class)
class InterestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InterestService interestService;

    private static final String HEADER_USER_ID = "Monew-Request-User-Id";
    private UUID userId;
    private InterestDto interestDto;
    private CursorPageResponseInterestDto cursorPageResponseDto;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        interestDto = new InterestDto(
            UUID.randomUUID(),
            "Test Interest",
            List.of("keyword1", "keyword2"),
            10L,
            true
        );

        cursorPageResponseDto = new CursorPageResponseInterestDto(
            List.of(interestDto),
            "Test Interest",
            Instant.now(),
            1,
            1L,
            false
        );
    }

    @Test
    @DisplayName("GET /api/interests - 성공 (200 OK): 관심사 목록 조회")
    void findAll_success() throws Exception {
        // given
        when(interestService.findAll(any(InterestQuery.class), eq(userId)))
            .thenReturn(cursorPageResponseDto);
        ArgumentCaptor<InterestQuery> queryCaptor = ArgumentCaptor.forClass(InterestQuery.class);

        // when & then
        mockMvc.perform(get("/api/interests")
                .header(HEADER_USER_ID, userId.toString())
                .param("keyword", "Test")
                .param("limit", "10")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value(interestDto.name()))
            .andExpect(jsonPath("$.totalElements").value(1L));

        // verify: 서비스 호출 및 @ModelAttribute 바인딩 검증
        verify(interestService).findAll(queryCaptor.capture(), eq(userId));
        InterestQuery capturedQuery = queryCaptor.getValue();
        assertThat(capturedQuery.keyword()).isEqualTo("Test");
        assertThat(capturedQuery.limit()).isEqualTo(10);
    }

    @Test
    @DisplayName("GET /api/interests - 실패 (400 Bad Request): 필수 헤더 누락")
    void findAll_fail_missingHeader() throws Exception {
        // when & then
        mockMvc.perform(get("/api/interests")
                .param("keyword", "Test")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andDo(print());

        // verify: 헤더 누락 시 서비스가 호출되지 않는지 검증
        verify(interestService, never()).findAll(any(), any());
    }

    @Test
    @DisplayName("POST /api/interests - 성공 (201 Created): 관심사 생성")
    void create_success() throws Exception {
        // given
        InterestRegisterRequest request = new InterestRegisterRequest("New Interest", List.of("k1"));
        InterestDto createdDto = new InterestDto(UUID.randomUUID(), "New Interest", List.of("k1"), 0L, true);

        when(interestService.create(any(InterestRegisterRequest.class)))
            .thenReturn(createdDto);

        ArgumentCaptor<InterestRegisterRequest> requestCaptor = ArgumentCaptor.forClass(InterestRegisterRequest.class);

        // when & then
        mockMvc.perform(post("/api/interests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("New Interest"));

        // verify: 서비스 호출 및 @RequestBody 바인딩 검증
        verify(interestService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().name()).isEqualTo("New Interest");
    }

    @DisplayName("POST /api/interests - 실패 (400 Bad Request): 유효성 검사 실패")
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidInterestRequests")
    void create_fail_validation(String testName, InterestRegisterRequest invalidRequest) throws Exception {
        // when & then
        mockMvc.perform(post("/api/interests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andDo(print());

        // verify: 유효성 검사 실패 시 서비스가 호출되지 않는지 검증
        verify(interestService, never()).create(any());
    }

    private static Stream<Arguments> invalidInterestRequests() {
        String nameTooLong = "a".repeat(51);
        List<String> keywordsTooMany = IntStream.range(0, 11)
            .mapToObj(i -> "k" + i)
            .toList();

        return Stream.of(
            Arguments.of("name: null", new InterestRegisterRequest(null, List.of("k1"))),
            Arguments.of("name: \"\" (min=1 위반)", new InterestRegisterRequest("", List.of("k1"))),
            Arguments.of("name: 51자 (max=50 위반)", new InterestRegisterRequest(nameTooLong, List.of("k1"))),
            Arguments.of("keywords: null", new InterestRegisterRequest("Valid Name", null)),
            Arguments.of("keywords: 0개 (min=1 위반)", new InterestRegisterRequest("Valid Name", Collections.emptyList())),
            Arguments.of("keywords: 11개 (max=10 위반)", new InterestRegisterRequest("Valid Name", keywordsTooMany))
        );
    }

    @Test
    @DisplayName("POST /api/interests/{id}/subscriptions - 성공 (200 OK): 관심사 구독")
    void subscribe_success() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();

        SubscriptionDto responseDto = new SubscriptionDto(
            UUID.randomUUID(),
            interestId,
            "Test Interest",
            List.of("k1"),
            1L,
            Instant.now()
        );

        // Service가 성공적으로 DTO를 반환하도록 Mocking
        when(interestService.subscribe(interestId, userId)).thenReturn(responseDto);

        // when & then
        mockMvc.perform(post("/api/interests/{interestId}/subscriptions", interestId) // POST
                .header(HEADER_USER_ID, userId.toString()) // 필수 헤더
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()) // @ResponseStatus(HttpStatus.OK) 검증
            .andExpect(jsonPath("$.id").value(responseDto.id().toString()))
            .andExpect(jsonPath("$.interestName").value("Test Interest"))
            .andDo(print());

        // verify: Service가 정확한 ID로 1회 호출되었는지 검증
        verify(interestService).subscribe(interestId, userId);
    }

    @Test
    @DisplayName("POST /api/interests/{id}/subscriptions - 실패 (400 Bad Request): 필수 헤더 누락")
    void subscribe_fail_missingHeader() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();

        // when & then: 헤더 없이 POST 요청
        mockMvc.perform(post("/api/interests/{interestId}/subscriptions", interestId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest()) // MissingRequestHeaderException (400)
            .andDo(print());

        // verify: 헤더가 없어 컨트롤러 진입 전 실패하므로 서비스가 호출되지 않음
        verify(interestService, never()).subscribe(any(), any());
    }

    @Test
    @DisplayName("POST /api/interests/{id}/subscriptions - 실패 (404 Not Found): 관심사 없음")
    void subscribe_fail_interestNotFound() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();

        // Service가 NotFoundException 던지도록 Mocking
        when(interestService.subscribe(interestId, userId))
            .thenThrow(new NotFoundException(ErrorCode.INTEREST_NOT_FOUND, "Interest not found"));

        // when & then
        mockMvc.perform(post("/api/interests/{interestId}/subscriptions", interestId)
                .header(HEADER_USER_ID, userId.toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andDo(print());

        // verify: Service는 호출되었으나 예외가 발생함
        verify(interestService).subscribe(interestId, userId);
    }
    @Test
    @DisplayName("DELETE /api/interests/{id}/subscriptions - 성공 (200 OK): 관심사 구독 취소")
    void unsubscribe_success() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();
        // (interestService.unsubscribe는 void를 반환하므로 when() Mocking이 필요 없음)

        // when & then
        mockMvc.perform(delete("/api/interests/{interestId}/subscriptions", interestId) // DELETE
                .header(HEADER_USER_ID, userId.toString())) // 필수 헤더
            .andExpect(status().isOk()) // @ResponseStatus(HttpStatus.OK) 검증
            .andDo(print());

        // verify: Service의 unsubscribe가 정확한 ID로 1회 호출되었는지 검증
        verify(interestService).unsubscribe(interestId, userId);
    }

    @Test
    @DisplayName("DELETE /api/interests/{id}/subscriptions - 실패 (400 Bad Request): 필수 헤더 누락")
    void unsubscribe_fail_missingHeader() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();

        // when & then: 헤더 없이 DELETE 요청
        mockMvc.perform(delete("/api/interests/{interestId}/subscriptions", interestId))
            .andExpect(status().isBadRequest()) // MissingRequestHeaderException (400)
            .andDo(print());

        // verify: 헤더가 없어 컨트롤러 진입 전 실패하므로 서비스가 호출되지 않음
        verify(interestService, never()).unsubscribe(any(), any());
    }

    @Test
    @DisplayName("DELETE /api/interests/{id} - 성공 (204 No Content): 관심사 삭제")
    void delete_success_shouldReturnNoContent() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();

        // 1. service.delete(id)가 호출될 때 아무것도 하지 않도록 설정 (void 메서드)
        doNothing().when(interestService).delete(interestId);

        // when & then
        mockMvc.perform(delete("/api/interests/{interestId}", interestId)) // DELETE
            .andExpect(status().isNoContent()) // @ResponseStatus(HttpStatus.NO_CONTENT) 검증
            .andDo(print());

        // verify: Service의 delete가 1회 호출되었는지 검증
        verify(interestService).delete(interestId);
    }

    @Test
    @DisplayName("DELETE /api/interests/{id} - 실패 (404 Not Found): 관심사 없음")
    void delete_fail_whenInterestNotFound() throws Exception {
        // given
        UUID fakeInterestId = UUID.randomUUID();

        // 1. service.delete(id)가 호출될 때 NotFoundException을 던지도록 Mocking
        doThrow(new NotFoundException(ErrorCode.INTEREST_NOT_FOUND, "Test Not Found"))
            .when(interestService)
            .delete(fakeInterestId);

        // when & then
        mockMvc.perform(delete("/api/interests/{interestId}", fakeInterestId))
            .andExpect(status().isNotFound())
            .andDo(print());

        // verify: Service의 delete가 1회 호출되었는지 검증
        verify(interestService).delete(fakeInterestId);
    }

    @Test
    @DisplayName("PATCH /api/interests/{id} - 성공 (200 OK): 관심사 키워드 수정")
    void update_success() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();
        InterestUpdateRequest request = new InterestUpdateRequest(List.of("k1", "k2"));

        // 1. 갱신된 DTO Mocking
        InterestDto updatedDto = new InterestDto(
            interestId,
            "Updated Name", // (이름은 바뀌지 않지만 DTO는 새로 생성됨)
            List.of("k1", "k2"),
            0L,
            null
        );
        when(interestService.update(eq(interestId), any(InterestUpdateRequest.class)))
            .thenReturn(updatedDto);

        // 2. 서비스로 전달된 request 캡처
        ArgumentCaptor<InterestUpdateRequest> requestCaptor =
            ArgumentCaptor.forClass(InterestUpdateRequest.class);

        // when & then
        mockMvc.perform(patch("/api/interests/{interestId}", interestId) // ⬅️ PATCH
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk()) // @ResponseStatus(HttpStatus.OK) 검증
            .andExpect(jsonPath("$.id").value(interestId.toString()))
            .andExpect(jsonPath("$.keywords[0]").value("k1"))
            .andDo(print());

        // verify: Service가 캡처된 request로 1회 호출되었는지 검증
        verify(interestService).update(eq(interestId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().keywords()).containsExactly("k1", "k2");
    }

    @Test
    @DisplayName("PATCH /api/interests/{id} - 실패 (404 Not Found): 갱신할 관심사 없음")
    void update_fail_whenInterestNotFound() throws Exception {
        // given
        UUID fakeInterestId = UUID.randomUUID();
        InterestUpdateRequest request = new InterestUpdateRequest(List.of("k1"));

        // 1. service.update(id)가 호출될 때 NotFoundException을 던지도록 Mocking
        when(interestService.update(eq(fakeInterestId), any(InterestUpdateRequest.class)))
            .thenThrow(new NotFoundException(ErrorCode.INTEREST_NOT_FOUND, "Test Not Found"));

        // when & then
        mockMvc.perform(patch("/api/interests/{interestId}", fakeInterestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound()) // (GlobalExceptionHandler가 404로 처리한다고 가정)
            .andDo(print());

        // verify
        verify(interestService).update(eq(fakeInterestId), any(InterestUpdateRequest.class));
    }

    @DisplayName("PATCH /api/interests/{id} - 실패 (400 Bad Request): 유효성 검사 실패")
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidInterestUpdateRequests") // ⬅️ DTO 유효성 검사용 새 MethodSource
    void update_fail_validation(String testName, InterestUpdateRequest invalidRequest) throws Exception {
        // given
        UUID interestId = UUID.randomUUID();

        // when & then
        mockMvc.perform(patch("/api/interests/{interestId}", interestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andDo(print());

        // verify: 유효성 검사 실패 시 서비스가 호출되지 않음
        verify(interestService, never()).update(any(), any());
    }

    private static Stream<Arguments> invalidInterestUpdateRequests() {
        List<String> keywordsTooMany = IntStream.range(0, 11) // @Size(max=10) 위반 (11개)
            .mapToObj(i -> "k" + i)
            .toList();

        return Stream.of(
            Arguments.of("keywords: null", new InterestUpdateRequest(null)),
            Arguments.of("keywords: 0개 (min=1 위반)", new InterestUpdateRequest(Collections.emptyList())),
            Arguments.of("keywords: 11개 (max=10 위반)", new InterestUpdateRequest(keywordsTooMany))
        );
    }
}
