package com.monew.monew_server.domain.notification.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.monew.monew_server.domain.notification.dto.CursorPageResponse;
import com.monew.monew_server.domain.notification.dto.NotificationDto;
import com.monew.monew_server.domain.notification.entity.NotificationResourceType;
import com.monew.monew_server.domain.notification.service.NotificationService;
import com.monew.monew_server.exception.NotificationNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = NotificationController.class,
	excludeAutoConfiguration = {
		JpaRepositoriesAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class,
		SecurityAutoConfiguration.class
	}
)
@DisplayName("NotificationController 테스트")
class NotificationControllerTest {

	@MockitoBean
	private AuditingHandler auditingHandler;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMappingContext;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationService notificationService;

	private UUID userId;
	private UUID notificationId;
	private NotificationDto notificationDto;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		notificationId = UUID.randomUUID();

		notificationDto = new NotificationDto(
			notificationId,
			false,
			userId,
			"Test notification",
			NotificationResourceType.interest,
			UUID.randomUUID(),
			Instant.now(),
			Instant.now()
		);
	}

	@Test
	@DisplayName("확인되지 않은 알림 목록 조회 성공")
	void findAllNotConfirmed() throws Exception {
		// Given
		CursorPageResponse<NotificationDto> response = new CursorPageResponse<>(
			List.of(notificationDto),
			null,
			null,
			10,
			false,
			1L
		);

		given(notificationService.findAllNotConfirmed(eq(userId), isNull(), isNull(), eq(10)))
			.willReturn(response);

		// When & Then
		mockMvc.perform(get("/api/notifications")
				.header("Monew-Request-User-ID", userId)
				.param("limit", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	@DisplayName("cursor 파라미터 없이 조회")
	void findAllNotConfirmed_WithoutCursor() throws Exception {
		// Given
		CursorPageResponse<NotificationDto> response = new CursorPageResponse<>(
			List.of(notificationDto),
			null,
			null,
			50,
			true,
			100L
		);

		given(notificationService.findAllNotConfirmed(eq(userId), isNull(), isNull(), eq(50)))
			.willReturn(response);

		// When & Then
		mockMvc.perform(get("/api/notifications")
				.header("Monew-Request-User-ID", userId)
				.param("limit", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(50))
			.andExpect(jsonPath("$.hasNext").value(true));
	}

	@Test
	@DisplayName("다음 페이지가 있는 경우 nextAfter값 포함")
	void findAllNotConfirmed_WithNextAfter() throws Exception {
		// Given
		Instant nextAfter = Instant.now();
		CursorPageResponse<NotificationDto> response = new CursorPageResponse<>(
			List.of(notificationDto),
			null,
			nextAfter,
			10,
			true,
			50L
		);

		given(notificationService.findAllNotConfirmed(eq(userId), isNull(), isNull(), eq(10)))
			.willReturn(response);

		// When & Then
		mockMvc.perform(get("/api/notifications")
				.header("Monew-Request-User-ID", userId)
				.param("limit", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.nextAfter").isNotEmpty());
	}

	@Test
	@DisplayName("특정 알림 확인 성공")
	void confirm() throws Exception {
		// Given
		doNothing().when(notificationService).confirm(notificationId, userId);

		// When & Then
		mockMvc.perform(patch("/api/notifications/{notificationId}", notificationId)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Monew-Request-User-ID", userId))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("존재하지 않는 알림 확인")
	void confirm_NotFound() throws Exception {
		// Given
		doThrow(NotificationNotFoundException.withId(notificationId))
			.when(notificationService).confirm(notificationId, userId);

		// When & Then
		mockMvc.perform(patch("/api/notifications/{notificationId}", notificationId)
				.header("Monew-Request-User-ID", userId))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("모든 알림 일괄 확인")
	void confirmAll() throws Exception {
		// Given
		doNothing().when(notificationService).confirmAll(userId);

		// When & Then
		mockMvc.perform(patch("/api/notifications")
				.header("Monew-Request-User-ID", userId))
			.andExpect(status().isOk());
	}
}