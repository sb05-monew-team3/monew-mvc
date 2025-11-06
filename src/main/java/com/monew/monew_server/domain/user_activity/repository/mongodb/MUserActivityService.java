package com.monew.monew_server.domain.user_activity.repository.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.monew.monew_server.domain.user_activity.repository.mongodb.entity.MUserActivity;
import com.monew.monew_server.domain.user_activity.repository.mongodb.repository.MUserActivityRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MUserActivityService {

	private static final int MAX_ARRAY_SIZE = 10;
	private final MongoTemplate mongoTemplate;
	private final MUserActivityRepository mUserActivityRepository;

	public MUserActivity initializeUserActivity(UUID userId, String email, String nickname, Instant createdAt) {
		MUserActivity userActivity = MUserActivity.builder()
			.userId(userId)
			.email(email)
			.nickname(nickname)
			.createdAt(createdAt)
			.build();

		return mUserActivityRepository.save(userActivity);
	}

	public void addSubscription(UUID userId, MUserActivity.Subscription subscription) {
		Query query = new Query(Criteria.where("_id").is(userId));

		Update update = new Update()
			.push("subscriptions")
			.atPosition(0)
			.each(subscription);

		mongoTemplate.updateFirst(query, update, MUserActivity.class);
	}

	public void removeSubscription(UUID userId, UUID interestId) {
		Query query = new Query(Criteria.where("_id").is(userId));

		Update update = new Update()
			.pull("subscriptions", Query.query(Criteria.where("interestId").is(interestId)));

		mongoTemplate.updateFirst(query, update, MUserActivity.class);
	}

	public void addComment(UUID userId, MUserActivity.Comment comment) {
		Query query = new Query(Criteria.where("_id").is(userId));

		Update update = new Update()
			.push("comments")
			.atPosition(0)
			.slice(MAX_ARRAY_SIZE)
			.each(comment);

		mongoTemplate.updateFirst(query, update, MUserActivity.class);
	}

	public void addCommentLike(UUID userId, MUserActivity.CommentLike commentLike) {
		Query query = new Query(Criteria.where("_id").is(userId));

		Update update = new Update()
			.push("commentLikes")
			.atPosition(0)
			.slice(MAX_ARRAY_SIZE)
			.each(commentLike);

		mongoTemplate.updateFirst(query, update, MUserActivity.class);
	}

	public void addArticleView(UUID userId, MUserActivity.ArticleView articleView) {
		Query query = new Query(Criteria.where("_id").is(userId));

		Update update = new Update()
			.push("articleViews")
			.atPosition(0)
			.slice(MAX_ARRAY_SIZE)
			.each(articleView);

		mongoTemplate.updateFirst(query, update, MUserActivity.class);
	}

}
