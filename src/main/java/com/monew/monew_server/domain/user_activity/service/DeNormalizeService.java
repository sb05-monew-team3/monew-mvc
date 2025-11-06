package com.monew.monew_server.domain.user_activity.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import com.monew.monew_server.domain.user_activity.repository.mongodb.entity.MUserActivity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeNormalizeService {

	private final JdbcTemplate jdbcTemplate;
	private final MongoTemplate mongoTemplate;

	/**
	 * 전체 사용자 MongoDB로 밀어넣기
	 */
	public void syncAllUsers() {
		jdbcTemplate.query("SELECT id FROM users", new RowCallbackHandler() {
			@Override
			public void processRow(ResultSet rs) throws SQLException {
				UUID userId = UUID.fromString(rs.getString("id"));
				syncUser(userId);
			}
		});
	}

	/**
	 * 단일 사용자만 동기화
	 */
	public void syncUser(UUID userId) {
		// 1. user
		UserRow user = jdbcTemplate.query("""
			SELECT id, email, nickname, created_at
			FROM users
			WHERE id = ?
			""", rs -> rs.next()
			? new UserRow(
			UUID.fromString(rs.getString("id")),
			rs.getString("email"),
			rs.getString("nickname"),
			rs.getTimestamp("created_at").toInstant()
		)
			: null, userId);

		if (user == null) {
			return;
		}

		// 2. build doc
		MUserActivity doc = MUserActivity.builder()
			.userId(user.id())
			.email(user.email())
			.nickname(user.nickname())
			.createdAt(user.createdAt())
			.subscriptions(fetchSubscriptions(userId))
			.comments(fetchComments(userId))
			.commentLikes(fetchCommentLikes(userId))
			.articleViews(fetchArticleViews(userId))
			.build();

		// 3. save
		mongoTemplate.save(doc);
	}

	private List<MUserActivity.Subscription> fetchSubscriptions(UUID userId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
			SELECT
			    s.id               AS subscription_id,
			    s.created_at       AS subscription_created_at,
			    i.id               AS interest_id,
			    i.name             AS interest_name,
			    ik.name            AS keyword_name,
			    cnt.subscriber_cnt AS subscriber_cnt
			FROM subscriptions s
			JOIN interests i ON s.interest_id = i.id
			LEFT JOIN interest_keywords ik ON ik.interest_id = i.id
			LEFT JOIN (
			    SELECT interest_id, COUNT(*) AS subscriber_cnt
			    FROM subscriptions
			    GROUP BY interest_id
			) cnt ON cnt.interest_id = i.id
			WHERE s.user_id = ?
			ORDER BY s.created_at DESC
			LIMIT 50
			""", userId);

		Map<UUID, MUserActivity.Subscription> map = new LinkedHashMap<>();
		for (Map<String, Object> row : rows) {
			UUID subId = (UUID)row.get("subscription_id");
			MUserActivity.Subscription sub = map.get(subId);
			if (sub == null) {
				Number cnt = (Number)row.get("subscriber_cnt");   // ← 핵심
				sub = MUserActivity.Subscription.builder()
					.id(subId)
					.interestId((UUID)row.get("interest_id"))
					.interestName((String)row.get("interest_name"))
					.interestKeywords(new ArrayList<>())
					.interestSubscriberCount(cnt == null ? 0 : cnt.intValue())
					.createdAt(toInstant(row.get("subscription_created_at")))
					.build();
				map.put(subId, sub);
			}
			String keyword = (String)row.get("keyword_name");
			if (keyword != null) {
				sub.getInterestKeywords().add(keyword);
			}
		}
		return new ArrayList<>(map.values());
	}

	private List<MUserActivity.Comment> fetchComments(UUID userId) {
		return jdbcTemplate.query("""
			SELECT
			    c.id         AS comment_id,
			    c.article_id AS article_id,
			    a.title      AS article_title,
			    c.user_id    AS user_id,
			    u.nickname   AS user_nickname,
			    c.content    AS content,
			    c.created_at AS created_at,
			    COALESCE(cl.cnt, 0) AS like_count
			FROM comments c
			JOIN articles a ON c.article_id = a.id
			JOIN users u ON c.user_id = u.id
			LEFT JOIN (
			    SELECT comment_id, COUNT(*) AS cnt
			    FROM comment_likes
			    GROUP BY comment_id
			) cl ON cl.comment_id = c.id
			WHERE c.user_id = ?
			ORDER BY c.created_at DESC
			LIMIT 50
			""", (rs, n) -> MUserActivity.Comment.builder()
			.id(UUID.fromString(rs.getString("comment_id")))
			.articleId(UUID.fromString(rs.getString("article_id")))
			.articleTitle(rs.getString("article_title"))
			.userId(UUID.fromString(rs.getString("user_id")))
			.userNickname(rs.getString("user_nickname"))
			.content(rs.getString("content"))
			.likeCount(rs.getInt("like_count"))
			.createdAt(rs.getTimestamp("created_at").toInstant())
			.build(), userId);
	}

	private List<MUserActivity.CommentLike> fetchCommentLikes(UUID userId) {
		return jdbcTemplate.query("""
			SELECT
			    cl.id         AS like_id,
			    cl.created_at AS like_created_at,
			    c.id          AS comment_id,
			    c.content     AS comment_content,
			    c.created_at  AS comment_created_at,
			    c.user_id     AS comment_user_id,
			    u.nickname    AS comment_user_nickname,
			    a.id          AS article_id,
			    a.title       AS article_title,
			    COALESCE(clcnt.cnt, 0) AS comment_like_count
			FROM comment_likes cl
			JOIN comments c ON cl.comment_id = c.id
			JOIN users u ON c.user_id = u.id
			JOIN articles a ON c.article_id = a.id
			LEFT JOIN (
			    SELECT comment_id, COUNT(*) AS cnt
			    FROM comment_likes
			    GROUP BY comment_id
			) clcnt ON clcnt.comment_id = c.id
			WHERE cl.user_id = ?
			ORDER BY cl.created_at DESC
			LIMIT 50
			""", (rs, n) -> MUserActivity.CommentLike.builder()
			.id(UUID.fromString(rs.getString("like_id")))
			.createdAt(rs.getTimestamp("like_created_at").toInstant())
			.commentId(UUID.fromString(rs.getString("comment_id")))
			.articleId(UUID.fromString(rs.getString("article_id")))
			.articleTitle(rs.getString("article_title"))
			.commentUserId(UUID.fromString(rs.getString("comment_user_id")))
			.commentUserNickname(rs.getString("comment_user_nickname"))
			.commentContent(rs.getString("comment_content"))
			.commentLikeCount(rs.getInt("comment_like_count"))
			.commentCreatedAt(rs.getTimestamp("comment_created_at").toInstant())
			.build(), userId);
	}

	private List<MUserActivity.ArticleView> fetchArticleViews(UUID userId) {
		return jdbcTemplate.query("""
			SELECT
			    av.id          AS view_id,
			    av.created_at  AS view_created_at,
			    av.user_id     AS viewed_by,
			    a.id           AS article_id,
			    a.source       AS source,
			    a.source_url   AS source_url,
			    a.title        AS article_title,
			    a.publish_date AS article_published_date,
			    a.summary      AS article_summary,
			    COALESCE(cmt.cnt, 0)   AS article_comment_count,
			    COALESCE(avcnt.cnt, 0) AS article_view_count
			FROM article_views av
			JOIN articles a ON av.article_id = a.id
			LEFT JOIN (
			    SELECT article_id, COUNT(*) AS cnt
			    FROM comments
			    GROUP BY article_id
			) cmt ON cmt.article_id = a.id
			LEFT JOIN (
			    SELECT article_id, COUNT(*) AS cnt
			    FROM article_views
			    GROUP BY article_id
			) avcnt ON avcnt.article_id = a.id
			WHERE av.user_id = ?
			ORDER BY av.created_at DESC
			LIMIT 50
			""", (rs, n) -> MUserActivity.ArticleView.builder()
			.id(UUID.fromString(rs.getString("view_id")))
			.viewedBy(UUID.fromString(rs.getString("viewed_by")))
			.createdAt(rs.getTimestamp("view_created_at").toInstant())
			.articleId(UUID.fromString(rs.getString("article_id")))
			.source(rs.getString("source"))
			.sourceUrl(rs.getString("source_url"))
			.articleTitle(rs.getString("article_title"))
			.articlePublishedDate(rs.getTimestamp("article_published_date").toInstant())
			.articleSummary(rs.getString("article_summary"))
			.articleCommentCount(rs.getInt("article_comment_count"))
			.articleViewCount(rs.getInt("article_view_count"))
			.build(), userId);
	}

	private Instant toInstant(Object o) {
		if (o == null)
			return null;
		if (o instanceof Timestamp ts)
			return ts.toInstant();
		return null;
	}

	private record UserRow(UUID id, String email, String nickname, Instant createdAt) {
	}
}
