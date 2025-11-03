package com.monew.monew_server.domain.users.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.monew.monew_server.domain.user.dto.UserDto;
import com.monew.monew_server.domain.user.dto.UserLoginRequest;
import com.monew.monew_server.domain.user.dto.UserRegisterRequest;
import com.monew.monew_server.domain.user.dto.UserUpdateRequset;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import com.monew.monew_server.domain.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserService userService;

	private UUID userId;
	private User user;

	@BeforeEach
	void setup() {
		userId = UUID.randomUUID();
		user = User.builder()
			.id(userId)
			.email("test@email.com")
			.nickname("tester")
			.password("test1234!")
			.createdAt(Instant.now())
			.build();
	}

	@Nested
	@DisplayName("회원가입 테스트")
	class RegisterTest {

		@Test
		@DisplayName("정상적으로 회원가입 성공")
		void register_success() {
			// given
			UserRegisterRequest request = UserRegisterRequest.builder()
				.email("test@email.com")
				.nickname("tester")
				.password("test1234!")
				.build();

			given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
			given(userRepository.existsByNickname(request.getNickname())).willReturn(false);

			// when
			userService.register(request);

			// then
			then(userRepository).should(times(1)).save(any(User.class));
		}

		@Test
		@DisplayName("이메일 중복 시 예외 발생")
		void register_fail_duplicate_email() {
			UserRegisterRequest request = UserRegisterRequest.builder()
				.email("test@email.com")
				.nickname("tester")
				.password("test1234!")
				.build();

			given(userRepository.existsByEmail(request.getEmail())).willReturn(true);

			assertThatThrownBy(() -> userService.register(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 존재하는 이메일");
		}

		@Test
		@DisplayName("닉네임 중복 시 예외 발생")
		void register_fail_duplicate_nickname() {
			UserRegisterRequest request = UserRegisterRequest.builder()
				.email("test@email.com")
				.nickname("tester")
				.password("test1234!")
				.build();

			given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
			given(userRepository.existsByNickname(request.getNickname())).willReturn(true);

			assertThatThrownBy(() -> userService.register(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 존재하는 닉네임");
		}
	}

	@Nested
	@DisplayName("로그인 테스트")
	class LoginTest {

		@Test
		@DisplayName("정상 로그인 성공")
		void login_success() {
			UserLoginRequest request = UserLoginRequest.builder()
				.email("test@email.com")
				.password("test1234!")
				.build();

			given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(user));

			UserDto result = userService.login(request);

			assertThat(result.getEmail()).isEqualTo("test@email.com");
			assertThat(result.getNickname()).isEqualTo("tester");
		}

		@Test
		@DisplayName("이메일이 존재하지 않으면 예외 발생")
		void login_fail_email_not_found() {
			UserLoginRequest request = UserLoginRequest.builder()
				.email("notfound@email.com")
				.password("test1234!")
				.build();

			given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.empty());

			assertThatThrownBy(() -> userService.login(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("올바르지 않는 이메일");
		}

		@Test
		@DisplayName("비밀번호가 일치하지 않으면 예외 발생")
		void login_fail_wrong_password() {
			UserLoginRequest request = UserLoginRequest.builder()
				.email("test@email.com")
				.password("wrong123!")
				.build();

			given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(user));

			assertThatThrownBy(() -> userService.login(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("비밀번호가 올바르지 않습니다");
		}

		@Test
		@DisplayName("삭제된 사용자 로그인 시 예외 발생")
		void login_fail_deleted_user() {
			UserLoginRequest request = UserLoginRequest.builder()
				.email("test@email.com")
				.password("test1234!")
				.build();

			user.softDelete();
			given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(user));

			assertThatThrownBy(() -> userService.login(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("존재하지 않는 사용자");
		}
	}

	@Nested
	@DisplayName("닉네임 수정 테스트")
	class UpdateNicknameTest {

		@Test
		@DisplayName("정상적으로 닉네임 수정 성공")
		void updateNickname_success() {
			UserUpdateRequset request = UserUpdateRequset.builder()
				.nickname("newNick")
				.build();

			given(userRepository.findById(userId)).willReturn(Optional.of(user));
			given(userRepository.existsByNickname(request.getNickname())).willReturn(false);

			UserDto result = userService.updateNickname(userId, request);

			assertThat(result.getNickname()).isEqualTo("newNick");
			then(userRepository).should().save(user);
		}

		@Test
		@DisplayName("존재하지 않는 사용자 예외 발생")
		void updateNickname_fail_not_found() {
			UserUpdateRequset request = UserUpdateRequset.builder()
				.nickname("newNick")
				.build();

			given(userRepository.findById(any())).willReturn(Optional.empty());

			assertThatThrownBy(() -> userService.updateNickname(UUID.randomUUID(), request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("존재하지 않는 사용자");
		}

		@Test
		@DisplayName("닉네임 중복 시 예외 발생")
		void updateNickname_fail_duplicate_nickname() {
			UserUpdateRequset request = UserUpdateRequset.builder()
				.nickname("newNick")
				.build();

			given(userRepository.findById(userId)).willReturn(Optional.of(user));
			given(userRepository.existsByNickname(request.getNickname())).willReturn(true);

			assertThatThrownBy(() -> userService.updateNickname(userId, request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 존재하는 닉네임");
		}
	}

	@Nested
	@DisplayName("사용자 삭제 테스트")
	class DeleteUserTest {

		@Test
		@DisplayName("소프트 삭제 성공")
		void softDelete_success() {
			given(userRepository.findById(userId)).willReturn(Optional.of(user));

			userService.deleteUser(userId);

			assertThat(user.isDeleted()).isTrue();
		}

		@Test
		@DisplayName("이미 삭제된 사용자 삭제 시 예외 발생")
		void softDelete_fail_already_deleted() {
			user.softDelete();
			given(userRepository.findById(userId)).willReturn(Optional.of(user));

			assertThatThrownBy(() -> userService.deleteUser(userId))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 삭제된 사용자");
		}

		@Test
		@DisplayName("하드 삭제 성공")
		void hardDelete_success() {
			user.softDelete();
			given(userRepository.findById(userId)).willReturn(Optional.of(user));

			userService.hardDeleteUser(userId);

			then(userRepository).should().delete(user);
		}

		@Test
		@DisplayName("소프트 삭제되지 않은 사용자는 하드 삭제 불가")
		void hardDelete_fail_not_soft_deleted() {
			given(userRepository.findById(userId)).willReturn(Optional.of(user));

			assertThatThrownBy(() -> userService.hardDeleteUser(userId))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("소프트 삭제되지 않는 사용자");
		}
	}
}
