package com.monew.monew_server.domain.comment.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.comment.dto.CommentDto;
import com.monew.monew_server.domain.comment.dto.CommentRegisterRequest;
import com.monew.monew_server.domain.comment.dto.CommentUpdateRequest;
import com.monew.monew_server.domain.comment.dto.CursorPageResponse;
import com.monew.monew_server.domain.comment.entity.Comment;
import com.monew.monew_server.domain.comment.repository.CommentLikeRepository;
import com.monew.monew_server.domain.comment.repository.CommentRepository;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user_activity.repository.mongodb.MUserActivityService;
import com.monew.monew_server.domain.user_activity.repository.mongodb.entity.MUserActivity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

	private final CommentRepository commentRepository;
	private final CommentLikeRepository commentLikeRepository;
	private final EntityManager entityManager;
	private final MUserActivityService mUserActivityService;

	@Transactional
	public CommentDto createComment(CommentRegisterRequest request) {
		log.info("댓글 생성 요청: articleId={}, userId={}",
			request.getArticleId(), request.getUserId());

		Article article = entityManager.getReference(Article.class, request.getArticleId());
		User user = entityManager.getReference(User.class, request.getUserId());

		Comment comment = Comment.builder()
			.article(article)
			.user(user)
			.content(request.getContent())
			.build();

		Comment savedComment = commentRepository.save(comment);

		log.info("댓글 생성 완료: commentId={}", savedComment.getId());

		saveMongoComment(user.getId(), savedComment);

		return convertToDto(savedComment);
	}

	private CommentDto convertToDto(Comment comment) {
		return CommentDto.builder()
			.id(comment.getId())
			.articleId(comment.getArticle() != null ? comment.getArticle().getId() : null)
			.userId(comment.getUser() != null ? comment.getUser().getId() : null)
			.userNickname(comment.getUser() != null ? comment.getUser().getNickname() : "임시닉네임")
			.content(comment.getContent())
			.likeCount(0L)
			.likedByMe(false)
			.createdAt(comment.getCreatedAt())
			.build();
	}

	@Transactional(readOnly = true)
	public CursorPageResponse<CommentDto> getComments(
		UUID articleId,
		String orderBy,
		String direction,
		String cursor,
		Instant after,
		int limit,
		UUID userId
	) {
		log.info("댓글 조회 요청: articleId={}, orderBy={}, direction={}, cursor={}, limit={}, userId={}",
			articleId, orderBy, direction, cursor, limit, userId);

		List<Comment> comments = commentRepository.findByArticleIdWithCursor(
			articleId, orderBy, direction, cursor, after, limit
		);

		log.info("조회된 댓글 수: {} (limit: {})", comments.size(), limit);

		boolean hasNext = comments.size() > limit;
		List<Comment> actualComments = hasNext ? comments.subList(0, limit) : comments;

		List<UUID> commentIds = actualComments.stream()
			.map(Comment::getId)
			.collect(Collectors.toList());

		Set<UUID> likedCommentIds = getLikedCommentIds(commentIds, userId);
		Map<UUID, Long> likeCountMap = getLikeCountMap(commentIds);

		List<CommentDto> commentDtos = actualComments.stream()
			.map(comment -> convertToDtoWithLikes(comment, likedCommentIds, likeCountMap))
			.collect(Collectors.toList());

		String nextCursor = null;
		Instant nextAfter = null;
		if (hasNext && !actualComments.isEmpty()) {
			Comment lastComment = actualComments.get(actualComments.size() - 1);

			if ("likeCount".equalsIgnoreCase(orderBy)) {
				Long likeCount = likeCountMap.getOrDefault(lastComment.getId(), 0L);
				nextCursor = likeCount.toString();
			} else {
				nextCursor = lastComment.getCreatedAt().toString();
			}

			nextAfter = lastComment.getCreatedAt();
		}

		long totalElements = commentRepository.countByArticleId(articleId);

		return CursorPageResponse.<CommentDto>builder()
			.content(commentDtos)
			.nextCursor(nextCursor)
			.nextAfter(nextAfter)
			.size(commentDtos.size())
			.totalElements(totalElements)
			.hasNext(hasNext)
			.build();
	}

	private Set<UUID> getLikedCommentIds(List<UUID> commentIds, UUID userId) {
		if (commentIds.isEmpty() || userId == null) {
			return Set.of();
		}

		return commentLikeRepository.findByCommentIdsAndUserId(commentIds, userId)
			.stream()
			.map(commentLike -> commentLike.getComment().getId())
			.collect(Collectors.toSet());
	}

	private Map<UUID, Long> getLikeCountMap(List<UUID> commentIds) {
		if (commentIds.isEmpty()) {
			return Map.of();
		}

		List<Object[]> results = commentLikeRepository.countByCommentIds(commentIds);

		Map<UUID, Long> likeCountMap = new HashMap<>();
		for (Object[] result : results) {
			UUID commentId = (UUID)result[0];
			Long count = (Long)result[1];
			likeCountMap.put(commentId, count);
		}

		return likeCountMap;
	}

	private CommentDto convertToDtoWithLikes(
		Comment comment,
		Set<UUID> likedCommentIds,
		Map<UUID, Long> likeCountMap
	) {
		return CommentDto.builder()
			.id(comment.getId())
			.articleId(comment.getArticle() != null ? comment.getArticle().getId() : null)
			.userId(comment.getUser() != null ? comment.getUser().getId() : null)
			.userNickname(comment.getUser() != null ? comment.getUser().getNickname() : "탈퇴한 사용자")
			.content(comment.getContent())
			.likeCount(likeCountMap.getOrDefault(comment.getId(), 0L))
			.likedByMe(likedCommentIds.contains(comment.getId()))
			.createdAt(comment.getCreatedAt())
			.build();
	}

	@Transactional
	public CommentDto updateComment(UUID commentId, UUID userId, CommentUpdateRequest request) {
		log.info("댓글 수정 요청: commentId={}, userId={}", commentId, userId);

		Comment comment = commentRepository.findById(commentId)
			.orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다: " + commentId));

		if (comment.isDeleted()) {
			log.warn("삭제된 댓글 수정 시도: commentId={}", commentId);
			throw new IllegalStateException("삭제된 댓글은 수정할 수 없습니다.");
		}

		if (!comment.getUser().getId().equals(userId)) {
			log.warn("권한 없음: 댓글 작성자({})와 요청자({})가 다름", comment.getUser().getId(), userId);
			throw new IllegalArgumentException("본인이 작성한 댓글만 수정할 수 있습니다.");
		}

		comment.setContent(request.getContent());

		log.info("댓글 수정 완료: commentId={}", commentId);

		return convertToDto(comment);
	}

	@Transactional
	public void deleteComment(UUID commentId, UUID userId) {
		log.info("댓글 논리 삭제 요청: commentId={}, userId={}", commentId, userId);

		Comment comment = commentRepository.findById(commentId)
			.orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다: " + commentId));

		if (!comment.getUser().getId().equals(userId)) {
			log.warn("권한 없음: 댓글 작성자({})와 요청자({})가 다름", comment.getUser().getId(), userId);
			throw new IllegalArgumentException("본인이 작성한 댓글만 삭제할 수 있습니다.");
		}

		if (comment.isDeleted()) {
			log.warn("이미 삭제된 댓글: commentId={}", commentId);
			throw new IllegalStateException("이미 삭제된 댓글입니다.");
		}

		comment.softDelete();

		log.info("댓글 논리 삭제 완료: commentId={}", commentId);
	}

	@Transactional
	public void hardDeleteComment(UUID commentId) {
		log.info("댓글 물리 삭제 요청: commentId={}", commentId);

		Comment comment = commentRepository.findById(commentId)
			.orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다: " + commentId));

		commentRepository.delete(comment);

		log.info("댓글 물리 삭제 완료: commentId={}", commentId);
	}

	public void saveMongoComment(UUID userId, Comment comment) {
		int count = (int)commentLikeRepository.countByComment_Id(comment.getId());
		MUserActivity.Comment buildComment = MUserActivity.Comment.builder()
			.id(comment.getId())
			.articleId(comment.getArticle().getId())
			.articleTitle(comment.getArticle().getTitle())
			.userId(userId)
			.userNickname(comment.getUser().getNickname())
			.content(comment.getContent())
			.likeCount(count)
			.createdAt(comment.getCreatedAt())
			.build();

		mUserActivityService.addComment(userId, buildComment);
	}
}