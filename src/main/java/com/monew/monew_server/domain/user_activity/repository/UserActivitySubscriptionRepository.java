package com.monew.monew_server.domain.user_activity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.interest.entity.Subscription;

@Repository
public interface UserActivitySubscriptionRepository extends JpaRepository<Subscription, UUID> {
	// 사용자별 구독 중인 관심사 (최근 10개)
	@EntityGraph(attributePaths = {"interest"})
	List<Subscription> findTop10ByUser_IdOrderByCreatedAtDesc(UUID userId);

	// 추가
	long countByInterest_Id(UUID interestId);
}
