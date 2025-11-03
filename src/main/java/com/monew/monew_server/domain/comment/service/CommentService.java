package com.monew.monew_server.domain.comment.service;
import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.comment.dto.CommentDto;
import com.monew.monew_server.domain.comment.dto.CommentRegisterRequest;
import com.monew.monew_server.domain.comment.dto.CommentUpdateRequest;
import com.monew.monew_server.domain.comment.dto.CursorPageResponse;
import com.monew.monew_server.domain.comment.entity.Comment;
import com.monew.monew_server.domain.comment.entity.CommentLike;
import com.monew.monew_server.domain.comment.repository.CommentLikeRepository;
import com.monew.monew_server.domain.comment.repository.CommentRepository;
import com.monew.monew_server.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final EntityManager entityManager;

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
            nextCursor = lastComment.getId().toString();
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
            UUID commentId = (UUID) result[0];
            Long count = (Long) result[1];
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

        // 1. 댓글 조회
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다: " + commentId));

        // 2. 삭제된 댓글 체크
        if (comment.isDeleted()) {
            log.warn("삭제된 댓글 수정 시도: commentId={}", commentId);
            throw new IllegalStateException("삭제된 댓글은 수정할 수 없습니다.");
        }

        // 3. 본인 확인 (작성자만 수정 가능)
        if (!comment.getUser().getId().equals(userId)) {
            log.warn("권한 없음: 댓글 작성자({})와 요청자({})가 다름", comment.getUser().getId(), userId);
            throw new IllegalArgumentException("본인이 작성한 댓글만 수정할 수 있습니다.");
        }

        // 4. 내용 수정
        comment.setContent(request.getContent());
        // JPA의 더티 체킹: @Transactional 내에서 엔티티 변경 시 자동으로 UPDATE 쿼리 실행
        // save() 호출 불필요! (하지만 명시적으로 save()를 호출해도 문제 없다고함)

        log.info("댓글 수정 완료: commentId={}", commentId);

        // 5. DTO 변환 후 반환
        return convertToDto(comment);
    }


    @Transactional
    public void deleteComment(UUID commentId, UUID userId) {
        log.info("댓글 논리 삭제 요청: commentId={}, userId={}", commentId, userId);

        // 1. 댓글 조회
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다: " + commentId));
        // 2. 사용자 본인인지 확인
        if (!comment.getUser().getId().equals(userId)) {
            log.warn("권한 없음: 댓글 작성자({})와 요청자({})가 다름", comment.getUser().getId(), userId);
            throw new IllegalArgumentException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        // 3. 이미 삭제된 댓글인지 체크
        if (comment.isDeleted()) {
            log.warn("이미 삭제된 댓글: commentId={}", commentId);
            throw new IllegalStateException("이미 삭제된 댓글입니다.");
        }

        // 4. 논리 삭제 실행
        comment.softDelete();

        // JPA 더티 체킹으로 자동 UPDATE (deleted_at = now())
        log.info("댓글 논리 삭제 완료: commentId={}", commentId);
    }


    @Transactional
    public void hardDeleteComment(UUID commentId, UUID userId) {
        log.info("댓글 물리 삭제 요청: commentId={}, userId={}", commentId, userId);

        // 1. 댓글 조회 (존재 여부 확인)
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다: " + commentId));

        // 2. 사용자 본인인지 확인
        if (!comment.getUser().getId().equals(userId)) {
            log.warn("권한 없음: 댓글 작성자({})와 요청자({})가 다름", comment.getUser().getId(), userId);
            throw new IllegalArgumentException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        // 3. 물리 삭제 실행 (DB에서 완전히 제거)
        commentRepository.delete(comment);

        log.info("댓글 물리 삭제 완료: commentId={}", commentId);
    }


}
