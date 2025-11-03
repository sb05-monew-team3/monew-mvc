package com.monew.monew_server.domain.users.repository;

import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Nested
	@DisplayName("회원 저장 및 조회 테스트")
	class SaveAndFindTest {

		@Test
		@DisplayName("회원 저장 후 ID로 조회 성공")
		void save_and_findById_success() {
			User user = User.builder()
				.email("test@email.com")
				.nickname("tester")
				.password("test1234!")
				.createdAt(Instant.now())
				.build();

			User saved = userRepository.save(user);
			Optional<User> found = userRepository.findById(saved.getId());

			assertThat(found).isPresent();
			assertThat(found.get().getEmail()).isEqualTo("test@email.com");
		}

		@Test
		@DisplayName("회원 저장 후 이메일로 조회 성공")
		void findByEmail_success() {
			User user = User.builder()
				.email("abc@test.com")
				.nickname("abc")
				.password("pw!")
				.createdAt(Instant.now())
				.build();

			userRepository.save(user);

			Optional<User> found = userRepository.findByEmail("abc@test.com");

			assertThat(found).isPresent();
			assertThat(found.get().getNickname()).isEqualTo("abc");
		}
	}

	@Nested
	@DisplayName("중복 검사 테스트")
	class ExistsTest {

		@Test
		@DisplayName("existsByEmail 정상 동작")
		void existsByEmail_success() {
			User user = User.builder()
				.email("dup@test.com")
				.nickname("dupNick")
				.password("pw!")
				.createdAt(Instant.now())
				.build();

			userRepository.save(user);

			boolean exists = userRepository.existsByEmail("dup@test.com");
			assertThat(exists).isTrue();
		}

		@Test
		@DisplayName("existsByNickname 정상 동작")
		void existsByNickname_success() {
			User user = User.builder()
				.email("unique@test.com")
				.nickname("dupNick2")
				.password("pw!")
				.createdAt(Instant.now())
				.build();

			userRepository.save(user);

			boolean exists = userRepository.existsByNickname("dupNick2");
			assertThat(exists).isTrue();
		}
	}

	@Nested
	@DisplayName("삭제 테스트")
	class DeleteTest {

		@Test
		@DisplayName("deleteById 정상 작동")
		void deleteById_success() {
			User user = User.builder()
				.email("del@test.com")
				.nickname("deleter")
				.password("pw!")
				.createdAt(Instant.now())
				.build();

			User saved = userRepository.save(user);
			UUID id = saved.getId();

			userRepository.deleteById(id);

			assertThat(userRepository.findById(id)).isEmpty();
		}
	}
}
