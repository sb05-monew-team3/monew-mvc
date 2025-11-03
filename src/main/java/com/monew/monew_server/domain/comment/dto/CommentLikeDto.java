package com.monew.monew_server.domain.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentLikeDto {

    private UUID id;
    private UUID likedBy;
    private Instant createdAt;

    private UUID commentId;
    private UUID articleId;
    private UUID commentUserId;
    private String commentUserNickname;
    private String commentContent;
    private Long commentLikeCount;
    private Instant commentCreatedAt;
}
