package com.monew.monew_server.domain.comment.controller;
import com.monew.monew_server.domain.comment.dto.CommentDto;
import com.monew.monew_server.domain.comment.dto.CommentLikeDto;
import com.monew.monew_server.domain.comment.dto.CommentRegisterRequest;
import com.monew.monew_server.domain.comment.dto.CommentUpdateRequest;
import com.monew.monew_server.domain.comment.dto.CursorPageResponse;
import com.monew.monew_server.domain.comment.service.CommentLikeService;
import com.monew.monew_server.domain.comment.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;



@Slf4j
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final CommentLikeService commentLikeService;

    @PostMapping
    public ResponseEntity<CommentDto> createComment(
            @Valid @RequestBody CommentRegisterRequest request
    ) {
        log.info("POST /api/comments - 댓글 생성 요청");

        CommentDto createdComment = commentService.createComment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdComment);
    }

    @GetMapping
    public ResponseEntity<CursorPageResponse<CommentDto>> getComments(
        @RequestParam UUID articleId,
        @RequestParam(defaultValue = "createdAt") String orderBy,
        @RequestParam(defaultValue = "ASC") String direction,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Instant after,
        @RequestParam(defaultValue = "50") int limit,
        @RequestHeader("Monew-Request-User-ID") UUID userId
    ) {
        log.info("GET /api/comments - articleId={}, orderBy={}, direction={}, cursor={}, limit={}, userId={}",
            articleId, orderBy, direction, cursor, limit, userId);

        CursorPageResponse<CommentDto> response = commentService.getComments(
            articleId, orderBy, direction, cursor, after, limit, userId
        );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<CommentDto> updateComment(
            @PathVariable UUID commentId,
            @RequestHeader("Monew-Request-User-ID") UUID userId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        log.info("PATCH /api/comments/{} - userId={}", commentId, userId);

        CommentDto updatedComment = commentService.updateComment(commentId, userId, request);

        return ResponseEntity.ok(updatedComment);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID commentId,
            @RequestHeader("Monew-Request-User-ID") UUID userId
    ) {
        log.info("DELETE /api/comments/{} - 논리 삭제 요청, userId={}", commentId, userId);

        commentService.deleteComment(commentId, userId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{commentId}/hard")
    public ResponseEntity<Void> deleteHardComment(
            @PathVariable UUID commentId,
            @RequestHeader("Monew-Request-User-ID") UUID userId
    ) {
        log.info("DELETE /api/comments/{}/hard - 물리 삭제 요청, userId={}", commentId, userId);

        commentService.hardDeleteComment(commentId, userId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{commentId}/comment-likes")
    public ResponseEntity<CommentLikeDto> addLike(
            @PathVariable UUID commentId,
            @RequestHeader("Monew-Request-User-ID") UUID userId
    ) {
        log.info("POST /api/comments/{}/comment-likes - 좋아요 추가, userId={}", commentId, userId);

        CommentLikeDto commentLike = commentLikeService.addLike(commentId, userId);

        return ResponseEntity.ok(commentLike);
    }

    @DeleteMapping("/{commentId}/comment-likes")
    public ResponseEntity<Void> removeLike(
            @PathVariable UUID commentId,
            @RequestHeader("Monew-Request-User-ID") UUID userId
    ) {
        log.info("DELETE /api/comments/{}/comment-likes - 좋아요 취소, userId={}", commentId, userId);

        commentLikeService.removeLike(commentId, userId);

        return ResponseEntity.ok().build();
    }
}
