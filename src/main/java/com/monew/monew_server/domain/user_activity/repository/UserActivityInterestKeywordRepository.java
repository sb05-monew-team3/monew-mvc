package com.monew.monew_server.domain.user_activity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.interest.entity.InterestKeyword;

@Repository
public interface UserActivityInterestKeywordRepository extends JpaRepository<InterestKeyword, UUID> {
	List<InterestKeyword> findByInterest_Id(UUID interestId);
}
