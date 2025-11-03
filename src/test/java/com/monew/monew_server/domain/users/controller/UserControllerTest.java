package com.monew.monew_server.domain.users.controller;

import com.monew.monew_server.domain.user.controller.UserController;
import com.monew.monew_server.domain.user.dto.*;
import com.monew.monew_server.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class UserControllerTest {

	private UserService userService;
	private UserController userController;

	@BeforeEach
	void setup() {
		userService = Mockito.mock(UserService.class);
		userController = new UserController(userService);
	}

	@Nested
	@DisplayName("회원가입 테스트")
	class RegisterTest {

		@Test
		@DisplayName("회원가입 성공 시 200 OK 반환")
		void registerUser_success() {
			// given
			UserRegisterRequest request = UserRegisterRequest.builder()
				.email("test@email.com")
				.nickname("tester")
				.password("test1234!")
				.build();

			Mockito.doNothing().when(userService).register(any(UserRegisterRequest.class));

			// when
			ResponseEntity<String> response = userController.registerUser(request);

			// then
			assertThat(response.getStatusCode().value()).isEqualTo(200);
			assertThat(response.getBody()).isEqualTo("회원가입이 완료되었습니다.");
		}
	}

	@Nested
	@DisplayName("로그인 테스트")
	class LoginTest {

		@Test
		@DisplayName("정상 로그인 시 UserDto 반환")
		void loginUser_success() {
			UserLoginRequest request = UserLoginRequest.builder()
				.email("test@email.com")
				.password("1234")
				.build();

			UserDto dto = UserDto.builder()
				.id(UUID.randomUUID())
				.email("test@email.com")
				.nickname("tester")
				.createdAt(OffsetDateTime.now(ZoneOffset.UTC))
				.build();

			Mockito.when(userService.login(any(UserLoginRequest.class))).thenReturn(dto);

			ResponseEntity<UserDto> response = userController.loginUser(request);

			assertThat(response.getStatusCode().value()).isEqualTo(200);
			assertThat(response.getBody()).isNotNull();
			assertThat(response.getBody().getEmail()).isEqualTo("test@email.com");
		}
	}

	@Nested
	@DisplayName("닉네임 수정 테스트")
	class UpdateNicknameTest {

		@Test
		@DisplayName("정상적으로 닉네임 수정 성공")
		void updateNickname_success() {
			UUID userId = UUID.randomUUID();
			UserUpdateRequset request = UserUpdateRequset.builder()
				.nickname("newNick")
				.build();

			UserDto dto = UserDto.builder()
				.id(userId)
				.email("test@email.com")
				.nickname("newNick")
				.createdAt(OffsetDateTime.now(ZoneOffset.UTC))
				.build();

			Mockito.when(userService.updateNickname(eq(userId), any(UserUpdateRequset.class)))
				.thenReturn(dto);

			ResponseEntity<UserDto> response = userController.updateNickname(userId, request);

			assertThat(response.getStatusCode().value()).isEqualTo(200);
			assertThat(response.getBody().getNickname()).isEqualTo("newNick");
		}
	}

	@Nested
	@DisplayName("삭제 테스트")
	class DeleteUserTest {

		@Test
		@DisplayName("소프트 삭제 성공 시 OK 응답")
		void deleteUser_success() {
			UUID userId = UUID.randomUUID();
			Mockito.doNothing().when(userService).deleteUser(userId);

			ResponseEntity<String> response = userController.deleteUser(userId);

			assertThat(response.getStatusCode().value()).isEqualTo(200);
			assertThat(response.getBody()).isEqualTo("회원이 삭제되었습니다.");
		}

		@Test
		@DisplayName("하드 삭제 성공 시 OK 응답")
		void hardDeleteUser_success() {
			UUID userId = UUID.randomUUID();
			Mockito.doNothing().when(userService).hardDeleteUser(userId);

			ResponseEntity<String> response = userController.hardDeleteUser(userId);

			assertThat(response.getStatusCode().value()).isEqualTo(200);
			assertThat(response.getBody()).isEqualTo("회원의 데이터가 영구적으로 삭제되었습니다.");
		}
	}
}
