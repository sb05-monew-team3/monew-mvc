package com.monew.monew_server.domain.comment.service;

import com.monew.monew_server.domain.comment.dto.CommentLikeDto;
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

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentLikeService {

    private final CommentLikeRepository commentLikeRepository;
    private final CommentRepository commentRepository;
    private final EntityManager entityManager;

    @Transactional
    public CommentLikeDto addLike(UUID commentId, UUID userId) {
        log.info("좋아요 추가 요청: commentId={}, userId={}", commentId, userId);

        // 1. 댓글이 존재하는지 확인
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다: " + commentId));

        // 2. 삭제된 댓글인지 체크
        if (comment.isDeleted()) {
            throw new IllegalStateException("삭제된 댓글에는 좋아요를 누를 수 없습니다.");
        }

        // 3. 이미 좋아요를 눌렀는지 체크 (중복 방지)
        if (commentLikeRepository.findByComment_IdAndUser_Id(commentId, userId).isPresent()) {
            throw new IllegalStateException("이미 좋아요를 누른 댓글입니다.");
        }

        // 4. User 객체 가져오기 (userId만 있으면 되니까 getReference 사용)
        User user = entityManager.getReference(User.class, userId);

        // 5. CommentLike 엔티티 생성
        CommentLike commentLike = CommentLike.builder()
                .comment(comment)
                .user(user)
                .build();

        // 6. DB에 저장
        CommentLike savedLike = commentLikeRepository.save(commentLike);
        log.info("좋아요 추가 완료: likeId={}", savedLike.getId());

        // 7. 현재 좋아요 개수 조회
        long likeCount = commentLikeRepository.countByComment_Id(commentId);

        // 8. 응답 DTO 생성 (Swagger 명세에 맞춰 댓글 정보도 함께 반환)
        return CommentLikeDto.builder()
                .id(savedLike.getId())
                .likedBy(userId)
                .createdAt(savedLike.getCreatedAt())
                .commentId(comment.getId())
                .articleId(comment.getArticle().getId())
                .commentUserId(comment.getUser().getId())
                .commentUserNickname(comment.getUser().getNickname())
                .commentContent(comment.getContent())
                .commentLikeCount(likeCount)
                .commentCreatedAt(comment.getCreatedAt())
                .build();
    }

    @Transactional
    public void removeLike(UUID commentId, UUID userId) {
        log.info("좋아요 취소 요청: commentId={}, userId={}", commentId, userId);

        // 1. 좋아요 찾기 (댓글ID + 유저ID로 조회)
        CommentLike commentLike = commentLikeRepository.findByComment_IdAndUser_Id(commentId, userId)
                .orElseThrow(() -> new EntityNotFoundException("좋아요를 찾을 수 없습니다."));

        // 2. DB에서 삭제
        commentLikeRepository.delete(commentLike);
        log.info("좋아요 취소 완료: commentId={}, userId={}", commentId, userId);
    }
}
