package com.monew.monew_server.domain.comment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.comment.entity.CommentLike;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {

    Optional<CommentLike> findByComment_IdAndUser_Id(UUID commentId, UUID userId);

    @Query("SELECT cl FROM CommentLike cl WHERE cl.comment.id IN :commentIds AND cl.user.id = :userId")
    List<CommentLike> findByCommentIdsAndUserId(
        @Param("commentIds") List<UUID> commentIds,
        @Param("userId") UUID userId
    );

    long countByComment_Id(UUID commentId);

    @Query("SELECT cl.comment.id, COUNT(cl) FROM CommentLike cl " +
           "WHERE cl.comment.id IN :commentIds " +
           "GROUP BY cl.comment.id")
    List<Object[]> countByCommentIds(@Param("commentIds") List<UUID> commentIds);
}
