package com.monew.monew_server.domain.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
	// 회원가입
	boolean existsByEmail(String email);
	boolean existsByNickname(String nickname);

	// 로그인
	Optional<User> findByEmail(String email);

	// 사용자 정보 조회
	@Query("""
		SELECT u
		FROM User u
		WHERE u.id = :userId
		""")
	Optional<User> findUserInfoById(@Param("userId") UUID userId);

}
