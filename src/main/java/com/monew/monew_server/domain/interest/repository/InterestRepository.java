package com.monew.monew_server.domain.interest.repository;

import com.monew.monew_server.domain.interest.entity.Interest;
import com.monew.monew_server.exception.ErrorCode;
import com.monew.monew_server.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterestRepository extends JpaRepository<Interest, UUID>, InterestQueryRepository {

    @Query(
        value =
            """
            SELECT *
            FROM interests
            WHERE (
                    1.0 - (levenshtein(name, :name) / (GREATEST(length(name), length(:name))::float))
                ) > 0.8
            """,
        nativeQuery = true)
    List<Interest> findSimilarInterests(@Param("name") String name);

    default Interest getOrThrow(UUID id) {
        return findById(id).orElseThrow(() ->
            new NotFoundException(ErrorCode.INTEREST_NOT_FOUND, "Interest not found with id: " + id)
        );
    }
}
