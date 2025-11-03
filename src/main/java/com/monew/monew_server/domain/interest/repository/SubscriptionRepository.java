package com.monew.monew_server.domain.interest.repository;

import com.monew.monew_server.domain.interest.entity.Subscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserIdAndInterestId(UUID userId, UUID interestId);

    long countByInterestId(UUID interestId);

    void deleteByUserIdAndInterestId(UUID userId, UUID interestId);
}
