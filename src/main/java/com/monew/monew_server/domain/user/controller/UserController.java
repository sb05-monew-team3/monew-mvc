package com.monew.monew_server.domain.user.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.monew.monew_server.domain.user.dto.UserDto;
import com.monew.monew_server.domain.user.dto.UserLoginRequest;
import com.monew.monew_server.domain.user.dto.UserRegisterRequest;
import com.monew.monew_server.domain.user.dto.UserUpdateRequset;
import com.monew.monew_server.domain.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
	private final UserService userService;

	@PostMapping
	public ResponseEntity<String> registerUser(@Valid @RequestBody UserRegisterRequest request) {
		userService.register(request);
		return ResponseEntity.ok("회원가입이 완료되었습니다.");
	}

	@PostMapping("/login")
	public ResponseEntity<UserDto> loginUser(@Valid @RequestBody UserLoginRequest request){
		UserDto userDto = userService.login(request);
		return ResponseEntity.ok(userDto);
	}

	@PatchMapping("/{userId}")
	public ResponseEntity<UserDto> updateNickname(@PathVariable UUID userId, @Valid @RequestBody UserUpdateRequset request) {
		UserDto userDto = userService.updateNickname(userId, request);
		return ResponseEntity.ok(userDto);
	}

	@DeleteMapping("/{userId}")
	public ResponseEntity<String> deleteUser(@PathVariable UUID userId) {
		userService.deleteUser(userId);
		return ResponseEntity.ok("회원이 삭제되었습니다.");
	}

	@DeleteMapping("/{userId}/hard")
	public ResponseEntity<String> hardDeleteUser(@PathVariable UUID userId){
		userService.hardDeleteUser(userId);
		return ResponseEntity.ok("회원의 데이터가 영구적으로 삭제되었습니다.");
	}



}
