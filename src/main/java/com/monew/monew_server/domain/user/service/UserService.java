package com.monew.monew_server.domain.user.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.monew.monew_server.domain.user.dto.UserDto;
import com.monew.monew_server.domain.user.dto.UserLoginRequest;
import com.monew.monew_server.domain.user.dto.UserRegisterRequest;
import com.monew.monew_server.domain.user.dto.UserUpdateRequset;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	private final UserRepository userRepository;

	public void register(UserRegisterRequest request) {
		// 이메일 중복 검사
		if (userRepository.existsByEmail(request.getEmail())) {
			throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
		}

		// 닉네임 중복 검사
		if (userRepository.existsByNickname(request.getNickname())) {
			throw new IllegalArgumentException("이미 존재하는 닉네임입니다.");
		}

		// 암호화 없이 비밀번호 그대로 저장 (임시)
		User user = User.builder()
			.email(request.getEmail())
			.nickname(request.getNickname())
			.password(request.getPassword())
			.build();

		userRepository.save(user);
	}

	@Transactional(readOnly = true)
	public UserDto login (UserLoginRequest request) {
		User user = userRepository.findByEmail(request.getEmail())
			.orElseThrow(() -> new IllegalArgumentException("올바르지 않는 이메일입니다."));

		if (user.getDeletedAt() != null) {
			throw new IllegalArgumentException("존재하지 않는 사용자입니다.");
		}

		if (!user.getPassword().equals(request.getPassword())){
			throw new IllegalArgumentException("비밀번호가 올바르지 않습니다.");
		}

		return UserDto.builder()
			.id(user.getId())
			.email(user.getEmail())
			.nickname(user.getNickname())
			.createdAt(user.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))
			.build();
	}

	@Transactional
	public UserDto updateNickname (UUID userId, UserUpdateRequset requset) {
		// 유저 존재 확인
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

		// 소프트 삭제 여부 확인
		if (user.getDeletedAt() != null) {
			throw new IllegalArgumentException("삭제된 사용자입니다.");
		}

		// 닉네임 중복 확인
		if (userRepository.existsByNickname(requset.getNickname())) {
			throw new IllegalArgumentException("이미 존재하는 닉네임 입니다.");
		}

		// 닉네임 수정
		user.setNickname(requset.getNickname());

		// 저장
		userRepository.save(user);

		// 응답 DTO 반환
		return UserDto.builder()
			.id(user.getId())
			.email(user.getEmail())
			.nickname(user.getNickname())
			.createdAt(user.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))
			.build();
	}

	@Transactional
	public void deleteUser(UUID id) {
		User user  = userRepository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자 입니다."));

		if (user.getDeletedAt() != null) {
			throw new IllegalArgumentException("이미 삭제된 사용자입니다.");
		}

		user.softDelete();
	}

	@Transactional
	public void hardDeleteUser(UUID userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자 입니다."));

		if (user.getDeletedAt() == null) {
			throw new IllegalArgumentException("소프트 삭제되지 않는 사용자는 하드 삭제할 수 없습니다.");
		}

		userRepository.delete(user);
	}


}
