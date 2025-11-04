package com.monew.monew_server.domain.user_activity.service;

import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.interest.entity.InterestKeyword;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import com.monew.monew_server.domain.user_activity.dto.ArticleViewSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.CommentLikeSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.CommentSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.SubscriptionSummaryDto;
import com.monew.monew_server.domain.user_activity.dto.UserActivityDto;
import com.monew.monew_server.domain.user_activity.mapper.UserActivityMapper;
import com.monew.monew_server.domain.user_activity.repository.UserActivityArticleViewRepository;
import com.monew.monew_server.domain.user_activity.repository.UserActivityCommentLikeRepository;
import com.monew.monew_server.domain.user_activity.repository.UserActivityCommentRepository;
import com.monew.monew_server.domain.user_activity.repository.UserActivityInterestKeywordRepository;
import com.monew.monew_server.domain.user_activity.repository.UserActivitySubscriptionRepository;
import com.monew.monew_server.exception.ErrorCode;
import com.monew.monew_server.exception.UserActivityException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityService {

	private final UserRepository userRepository;
	private final UserActivityMapper userActivityMapper;
	private final UserActivitySubscriptionRepository userActivitySubscriptionRepository;
	private final UserActivityCommentRepository userActivityCommentRepository;
	private final UserActivityCommentLikeRepository userActivityCommentLikeRepository;
	private final UserActivityArticleViewRepository userActivityArticleViewRepository;
	private final UserActivityInterestKeywordRepository userActivityInterestKeywordRepository;

	/**
	 * 사용자 전체 활동 조회
	 */

	@Transactional(readOnly = true)
	public UserActivityDto getUserActivity(UUID userId) {
		log.info("[UserActivityService] 사용자 활동 조회 요청 - userId={}", userId);

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserActivityException(ErrorCode.USER_NOT_FOUND));

		// 구독 중인 관심사 10개 조회
		List<SubscriptionSummaryDto> subscriptions = userActivitySubscriptionRepository
			.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
			.stream()
			.map(s -> {
				UUID interestId = s.getInterest().getId();

				// 🔹 InterestKeyword 테이블에서 키워드 조회
				List<String> keywords = userActivityInterestKeywordRepository.findByInterest_Id(interestId)
					.stream()
					.map(InterestKeyword::getName)
					.toList();

				// 🔹 Subscription 테이블에서 구독자 수 계산
				long subscriberCount = userActivitySubscriptionRepository.countByInterest_Id(interestId);

				return SubscriptionSummaryDto.builder()
					.id(s.getId())
					.interestId(interestId)
					.interestName(s.getInterest().getName())
					.interestKeywords(keywords)
					.interestSubscriberCount(subscriberCount)
					.createdAt(s.getCreatedAt())
					.build();
			})
			.toList();
		log.debug("[UserActivityService] 구독 관심사 {}건 조회 완료", subscriptions.size());

		// 최근 작성한 댓글
		List<CommentSummaryDto> comments = userActivityCommentRepository
			.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
			.stream()
			.map(c -> {
				long likeCount = userActivityCommentLikeRepository.countByComment_Id(c.getId()); // ✅ 좋아요 수 계산

				return CommentSummaryDto.builder()
					.id(c.getId())
					.articleId(c.getArticle().getId())
					.articleTitle(c.getArticle().getTitle())
					.userId(c.getUser().getId())
					.userNickname(c.getUser().getNickname())
					.content(c.getContent())
					.likeCount(likeCount)
					.createdAt(c.getCreatedAt())
					.build();
			})
			.toList();
		log.debug("[UserActivityService] 댓글 {}건 조회 완료", comments.size());

		// 최근 좋아요한 댓글
		List<CommentLikeSummaryDto> commentLikes = userActivityCommentLikeRepository
			.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
			.stream()
			.map(cl -> {
				UUID commentId = cl.getComment().getId();


				long likeCount = userActivityCommentLikeRepository.countByComment_Id(commentId);

				return CommentLikeSummaryDto.builder()
					.id(cl.getId())
					.createdAt(cl.getCreatedAt())
					.commentId(commentId)
					.articleId(cl.getComment().getArticle().getId())
					.articleTitle(cl.getComment().getArticle().getTitle())
					.commentUserId(cl.getComment().getUser().getId())
					.commentUserNickname(cl.getComment().getUser().getNickname())
					.commentContent(cl.getComment().getContent())
					.commentLikeCount(likeCount)
					.commentCreatedAt(cl.getComment().getCreatedAt())
					.build();
			})
			.toList();
		log.debug("[UserActivityService] 댓글 좋아요 {}건 조회 완료", commentLikes.size());

		// 최근 본 뉴스 기사
		List<ArticleViewSummaryDto> articleViews = userActivityArticleViewRepository
			.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
			.stream()
			.map(av -> {
				Article article = av.getArticle();

				long commentCount = userActivityCommentRepository.countByArticle_Id(article.getId());
				long viewCount = userActivityArticleViewRepository.countByArticle_id(article.getId());

				return ArticleViewSummaryDto.builder()
					.id(av.getId())
					.viewedBy(av.getUser().getId())
					.createdAt(av.getCreatedAt())
					.articleId(article.getId())
					.source(article.getSource().name())
					.sourceUrl(article.getSourceUrl())
					.articleTitle(article.getTitle())
					.articlePublishedDate(article.getPublishDate())
					.articleSummary(article.getSummary())
					.articleCommentCount(commentCount)
					.articleViewCount(viewCount)
					.build();
				})
				.toList();
		log.debug("[UserActivityService] 기사 조회 {}건 완료", articleViews.size());


		UserActivityDto result = userActivityMapper.toDto(
			user,
			subscriptions,
			comments,
			commentLikes,
			articleViews
		);

		log.info("[UserActivityService] 사용자 활동 조회 완료 - userId={}, totalCount(subs+comments+likes+views)={}",
			userId,
			subscriptions.size() + comments.size() + commentLikes.size() + articleViews.size());

		return result;
	}
}
