package com.monew.monew_server.domain.interest.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.monew.monew_server.domain.interest.entity.InterestKeyword;

public interface InterestKeywordRepository extends JpaRepository<InterestKeyword, UUID> {

	@Query("SELECT ik.name FROM InterestKeyword ik WHERE ik.interest.id = :interestId")
	List<String> findKeywordsByInterestId(@Param("interestId") UUID interestId);

	@Query("SELECT k FROM InterestKeyword k WHERE k.interest.id IN :interestIds")
	List<InterestKeyword> findByInterestIdIn(List<UUID> interestIds);

	void deleteAllByInterestId(UUID interestId);

}
