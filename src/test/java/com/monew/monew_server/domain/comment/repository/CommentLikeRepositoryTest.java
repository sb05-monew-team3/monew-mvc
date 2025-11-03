package com.monew.monew_server.domain.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.article.entity.ArticleSource;
import com.monew.monew_server.domain.article.repository.ArticleRepository;
import com.monew.monew_server.domain.comment.entity.Comment;
import com.monew.monew_server.domain.comment.entity.CommentLike;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class CommentLikeRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private CommentLikeRepository commentLikeRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private User user1;
    private User user2;
    private Article article1;
    private Comment comment1;
    private Comment comment2;

    @BeforeEach
    void setUp() {
        // 1. 유저 생성
        user1 = userRepository.save(User.builder()
            .email("test1@test.com")
            .nickname("테스터1")
            .password("password123")
            .build()
        );

        user2 = userRepository.save(User.builder()
            .email("test2@test.com")
            .nickname("테스터2")
            .password("password456")
            .build()
        );

        // 2. 기사 생성
        article1 = articleRepository.save(Article.builder()
            .title("테스트 기사")
            .summary("테스트 요약")
            .source(ArticleSource.NAVER)
            .sourceUrl("http://test.com")
            .publishDate(Instant.now())
            .build()
        );

        // 3. 댓글 생성
        comment1 = commentRepository.save(Comment.builder()
            .article(article1)
            .user(user1)
            .content("첫 번째 댓글")
            .build()
        );

        comment2 = commentRepository.save(Comment.builder()
            .article(article1)
            .user(user2)
            .content("두 번째 댓글")
            .build()
        );

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Repository - 댓글 좋아요 저장 성공")
    void shouldSaveCommentLike() {
        // given
        CommentLike commentLike = CommentLike.builder()
            .comment(comment1)
            .user(user1)
            .build();

        // when
        CommentLike saved = commentLikeRepository.save(commentLike);
        entityManager.flush();

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getComment().getId()).isEqualTo(comment1.getId());
        assertThat(saved.getUser().getId()).isEqualTo(user1.getId());
    }

    @Test
    @DisplayName("Repository - 특정 댓글의 좋아요 개수 조회")
    void shouldCountLikesByCommentId() {
        // given: comment1에 좋아요 2개
        commentLikeRepository.save(CommentLike.builder()
            .comment(comment1)
            .user(user1)
            .build());
        commentLikeRepository.save(CommentLike.builder()
            .comment(comment1)
            .user(user2)
            .build());
        entityManager.flush();

        // when
        long count = commentLikeRepository.countByComment_Id(comment1.getId());

        // then
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("Repository - 특정 사용자와 댓글로 좋아요 조회")
    void shouldFindByCommentIdAndUserId() {
        // given
        CommentLike like = commentLikeRepository.save(CommentLike.builder()
            .comment(comment1)
            .user(user1)
            .build());
        entityManager.flush();
        entityManager.clear();

        // when
        var result = commentLikeRepository.findByComment_IdAndUser_Id(comment1.getId(), user1.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(like.getId());
    }

    @Test
    @DisplayName("Repository - 좋아요가 없을 때 빈 Optional 반환")
    void shouldReturnEmptyWhenLikeNotFound() {
        // given: 좋아요가 없는 상태

        // when
        var result = commentLikeRepository.findByComment_IdAndUser_Id(comment1.getId(), user1.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Repository - 여러 댓글에 대한 사용자의 좋아요 조회")
    void shouldFindByCommentIdsAndUserId() {
        // given: user1이 comment1, comment2에 좋아요
        commentLikeRepository.save(CommentLike.builder()
            .comment(comment1)
            .user(user1)
            .build());
        commentLikeRepository.save(CommentLike.builder()
            .comment(comment2)
            .user(user1)
            .build());
        entityManager.flush();

        // when
        List<CommentLike> result = commentLikeRepository.findByCommentIdsAndUserId(
            List.of(comment1.getId(), comment2.getId()),
            user1.getId()
        );

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(like -> like.getComment().getId())
            .containsExactlyInAnyOrder(comment1.getId(), comment2.getId());
    }

    @Test
    @DisplayName("Repository - 여러 댓글의 좋아요 개수 조회")
    void shouldCountByCommentIds() {
        // given: comment1에 2개, comment2에 1개
        commentLikeRepository.save(CommentLike.builder()
            .comment(comment1)
            .user(user1)
            .build());
        commentLikeRepository.save(CommentLike.builder()
            .comment(comment1)
            .user(user2)
            .build());
        commentLikeRepository.save(CommentLike.builder()
            .comment(comment2)
            .user(user1)
            .build());
        entityManager.flush();

        // when
        List<Object[]> result = commentLikeRepository.countByCommentIds(
            List.of(comment1.getId(), comment2.getId())
        );

        // then
        assertThat(result).hasSize(2);

        // comment1: 2개
        Object[] comment1Count = result.stream()
            .filter(r -> r[0].equals(comment1.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(comment1Count[1]).isEqualTo(2L);

        // comment2: 1개
        Object[] comment2Count = result.stream()
            .filter(r -> r[0].equals(comment2.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(comment2Count[1]).isEqualTo(1L);
    }

    @Test
    @DisplayName("Repository - 빈 리스트로 좋아요 개수 조회 시 빈 결과 반환")
    void shouldReturnEmptyListWhenCommentIdsEmpty() {
        // given
        List<UUID> emptyList = List.of();

        // when
        List<Object[]> result = commentLikeRepository.countByCommentIds(emptyList);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Repository - 좋아요 삭제 성공")
    void shouldDeleteCommentLike() {
        // given
        CommentLike like = commentLikeRepository.save(CommentLike.builder()
            .comment(comment1)
            .user(user1)
            .build());
        UUID likeId = like.getId();
        entityManager.flush();

        // when
        commentLikeRepository.delete(like);
        entityManager.flush();

        // then
        assertThat(commentLikeRepository.findById(likeId)).isEmpty();
    }
}
