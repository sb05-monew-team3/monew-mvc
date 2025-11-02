package com.monew.monew_server.domain.user_activity.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import com.monew.monew_server.domain.user_activity.dto.UserInfoDto;
import com.monew.monew_server.domain.user_activity.mapper.UserActivityMapper;
import com.monew.monew_server.exception.ErrorCode;
import com.monew.monew_server.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserActivityService {

	private final UserRepository userRepository;
	private final UserActivityMapper userActivityMapper;

	// 사용자 기본 정보 조회 (닉네임, 이메일)
	public UserInfoDto getUserInfo(UUID userId) {
		// 사용자 조회
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: " + userId));

		return userActivityMapper.toUserInfoDto(user);
	}
}
