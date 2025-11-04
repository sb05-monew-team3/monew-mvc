package com.monew.monew_server.domain.user_activity.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.monew.monew_server.domain.user_activity.dto.UserActivityDto;
import com.monew.monew_server.domain.user_activity.service.UserActivityService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-activities")
public class UserActivityController {

	private final UserActivityService userActivityService;

	// 사용자 정보 조회
	@GetMapping("/{userId}")
	public ResponseEntity<UserActivityDto> getUserActivity(@PathVariable UUID userId) {
		UserActivityDto userActivity = userActivityService.getUserActivity(userId);
		return ResponseEntity.ok(userActivity);
	}
}
