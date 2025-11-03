package com.monew.monew_server.domain.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.article.entity.ArticleSource;
import com.monew.monew_server.domain.article.repository.ArticleRepository;
import com.monew.monew_server.domain.article.repository.projection.CommentCountProjection;
import com.monew.monew_server.domain.comment.entity.Comment;
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
class CommentRepositoryTest {

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

    @Autowired private CommentRepository commentRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private Article testArticle;
    private User testUser;

    @BeforeEach
    void setUp() {
        // 1. 테스트용 유저 생성
        testUser = userRepository.save(User.builder()
            .email("test@example.com")
            .nickname("테스터")
            .password("password123")
            .build()
        );

        // 2. 테스트용 Article 생성
        testArticle = articleRepository.save(Article.builder()
            .title("테스트 기사")
            .summary("테스트 요약")
            .source(ArticleSource.NAVER)
            .sourceUrl("http://test.com")
            .publishDate(Instant.now())
            .build()
        );

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Repository - 특정 기사의 댓글 조회")
    void shouldFindCommentsByArticleId() {
        // given
        Comment comment1 = commentRepository.save(Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("첫 번째 댓글")
            .build());

        Comment comment2 = commentRepository.save(Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("두 번째 댓글")
            .build());

        entityManager.flush();
        entityManager.clear();

        // when
        List<Comment> result = commentRepository.findByArticle_Id(testArticle.getId());

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("첫 번째 댓글");
        assertThat(result.get(1).getContent()).isEqualTo("두 번째 댓글");
    }

    @Test
    @DisplayName("Repository - 댓글이 없는 기사 조회 시 빈 리스트 반환")
    void shouldReturnEmptyListWhenNoComments() {
        // given
        UUID nonExistentArticleId = UUID.randomUUID();

        // when
        List<Comment> result = commentRepository.findByArticle_Id(nonExistentArticleId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Repository - 여러 기사의 댓글 수 조회")
    void shouldFindCommentCountsByArticleIds() {
        // given
        Article article2 = articleRepository.save(Article.builder()
            .title("두 번째 기사")
            .summary("요약")
            .source(ArticleSource.HANKYUNG)
            .sourceUrl("http://test2.com")
            .publishDate(Instant.now())
            .build()
        );
        entityManager.flush();

        // article1에 댓글 3개
        commentRepository.save(Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("댓글1")
            .build());
        commentRepository.save(Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("댓글2")
            .build());
        commentRepository.save(Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("댓글3")
            .build());

        // article2에 댓글 2개
        commentRepository.save(Comment.builder()
            .article(article2)
            .user(testUser)
            .content("댓글4")
            .build());
        commentRepository.save(Comment.builder()
            .article(article2)
            .user(testUser)
            .content("댓글5")
            .build());

        entityManager.flush();
        entityManager.clear();

        // when
        List<CommentCountProjection> result = commentRepository.findCommentCountsByArticleIds(
            List.of(testArticle.getId(), article2.getId())
        );

        // then
        assertThat(result).hasSize(2);

        CommentCountProjection article1Count = result.stream()
            .filter(c -> c.getArticleId().equals(testArticle.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(article1Count.getCommentCount()).isEqualTo(3L);

        CommentCountProjection article2Count = result.stream()
            .filter(c -> c.getArticleId().equals(article2.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(article2Count.getCommentCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Repository - 삭제된 댓글은 댓글 수에서 제외")
    void shouldExcludeDeletedCommentsFromCount() {
        // given
        Comment comment1 = commentRepository.save(Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("정상 댓글")
            .build());

        Comment comment2 = commentRepository.save(Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("삭제될 댓글")
            .build());

        // 댓글2 논리 삭제
        comment2.softDelete();
        commentRepository.save(comment2);
        entityManager.flush();
        entityManager.clear();

        // when
        List<CommentCountProjection> result = commentRepository.findCommentCountsByArticleIds(
            List.of(testArticle.getId())
        );

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCommentCount()).isEqualTo(1L); // 삭제된 댓글 제외
    }

    @Test
    @DisplayName("Repository - 특정 기사의 댓글 수 조회")
    void shouldCountCommentsByArticleId() {
        // given
        commentRepository.save(Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("댓글1")
            .build());

        commentRepository.save(Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("댓글2")
            .build());

        entityManager.flush();

        // when
        long count = commentRepository.countByArticleId(testArticle.getId());

        // then
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("Repository - 댓글이 없는 기사의 댓글 수는 0")
    void shouldReturnZeroCountWhenNoComments() {
        // given
        UUID nonExistentArticleId = UUID.randomUUID();

        // when
        long count = commentRepository.countByArticleId(nonExistentArticleId);

        // then
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("Repository - 댓글 저장 성공")
    void shouldSaveCommentSuccessfully() {
        // given
        Comment comment = Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("새로운 댓글")
            .build();

        // when
        Comment savedComment = commentRepository.save(comment);
        entityManager.flush();

        // then
        assertThat(savedComment.getId()).isNotNull();
        assertThat(savedComment.getContent()).isEqualTo("새로운 댓글");
        assertThat(savedComment.getArticle().getId()).isEqualTo(testArticle.getId());
        assertThat(savedComment.getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Repository - 댓글 삭제 성공")
    void shouldDeleteCommentSuccessfully() {
        // given
        Comment comment = commentRepository.save(Comment.builder()
            .article(testArticle)
            .user(testUser)
            .content("삭제할 댓글")
            .build());
        UUID commentId = comment.getId();
        entityManager.flush();

        // when
        commentRepository.delete(comment);
        entityManager.flush();

        // then
        assertThat(commentRepository.findById(commentId)).isEmpty();
    }

    @Test
    @DisplayName("Repository - 빈 리스트로 댓글 수 조회 시 빈 결과 반환")
    void shouldReturnEmptyListWhenArticleIdsEmpty() {
        // given
        List<UUID> emptyList = List.of();

        // when
        List<CommentCountProjection> result = commentRepository.findCommentCountsByArticleIds(emptyList);

        // then
        assertThat(result).isEmpty();
    }
}
