package com.monew.monew_server.domain.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
	// 회원가입
	boolean existsByEmail(String email);
	boolean existsByNickname(String nickname);

	// 로그인
	Optional<User> findByEmail(String email);

}
