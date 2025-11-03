package com.monew.monew_server.domain.interest.repository;

import com.monew.monew_server.domain.interest.entity.InterestKeyword;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterestKeywordRepository extends JpaRepository<InterestKeyword, UUID> {

    @Query("SELECT ik.name FROM InterestKeyword ik WHERE ik.interest.id = :interestId")
    List<String> findKeywordsByInterestId(@Param("interestId") UUID interestId);

    void deleteAllByInterestId(UUID interestId);
}
