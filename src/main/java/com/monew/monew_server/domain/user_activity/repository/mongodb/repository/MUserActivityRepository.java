package com.monew.monew_server.domain.user_activity.repository.mongodb.repository;

import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.user_activity.repository.mongodb.entity.MUserActivity;

@Repository
public interface MUserActivityRepository extends MongoRepository<MUserActivity, UUID> {

	MUserActivity findByUserId(UUID userId);

}
