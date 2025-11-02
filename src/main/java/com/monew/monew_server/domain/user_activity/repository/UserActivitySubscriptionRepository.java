package com.monew.monew_server.domain.user_activity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.monew.monew_server.domain.interest.entity.Subscription;

public interface UserActivitySubscriptionRepository extends JpaRepository<Subscription, UUID> {
	// 사용자별 구독 중인 관심사
	List<Subscription> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);
}
