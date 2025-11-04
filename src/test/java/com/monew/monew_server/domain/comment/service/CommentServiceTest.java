package com.monew.monew_server.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.article.entity.ArticleSource;
import com.monew.monew_server.domain.article.repository.ArticleRepository;
import com.monew.monew_server.domain.comment.dto.CommentDto;
import com.monew.monew_server.domain.comment.dto.CommentRegisterRequest;
import com.monew.monew_server.domain.comment.dto.CommentUpdateRequest;
import com.monew.monew_server.domain.comment.dto.CursorPageResponse;
import com.monew.monew_server.domain.comment.entity.Comment;
import com.monew.monew_server.domain.comment.repository.CommentRepository;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
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
class CommentServiceTest {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private CommentService commentService;
    @Autowired private CommentRepository commentRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private User user1;
    private User user2;
    private Article article1;

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

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("createComment - 댓글 생성 성공")
    void createComment_Success() {
        // given
        CommentRegisterRequest request = new CommentRegisterRequest(
            article1.getId(),
            user1.getId(),
            "새로운 댓글입니다"
        );

        // when
        CommentDto result = commentService.createComment(request);
        entityManager.flush();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getContent()).isEqualTo("새로운 댓글입니다");
        assertThat(result.getArticleId()).isEqualTo(article1.getId());
        assertThat(result.getUserId()).isEqualTo(user1.getId());
        assertThat(result.getUserNickname()).isEqualTo("테스터1");

        // DB 검증
        assertThat(commentRepository.findById(result.getId())).isPresent();
    }

    @Test
    @DisplayName("getComments - 댓글 목록 조회 성공 (커서 페이징)")
    void getComments_Success() {
        // given: 댓글 3개 생성
        commentRepository.save(Comment.builder()
            .article(article1)
            .user(user1)
            .content("첫 번째 댓글")
            .build());
        commentRepository.save(Comment.builder()
            .article(article1)
            .user(user2)
            .content("두 번째 댓글")
            .build());
        commentRepository.save(Comment.builder()
            .article(article1)
            .user(user1)
            .content("세 번째 댓글")
            .build());
        entityManager.flush();
        entityManager.clear();

        // when: 1페이지 조회 (limit=2)
        CursorPageResponse<CommentDto> response = commentService.getComments(
            article1.getId(), "createdAt", "ASC", null, null, 2, user1.getId()
        );

        // then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getContent().get(0).getContent()).isEqualTo("첫 번째 댓글");
        assertThat(response.getContent().get(1).getContent()).isEqualTo("두 번째 댓글");
    }

    @Test
    @DisplayName("updateComment - 댓글 수정 성공")
    void updateComment_Success() {
        // given
        Comment comment = commentRepository.save(Comment.builder()
            .article(article1)
            .user(user1)
            .content("원래 댓글 내용")
            .build());
        entityManager.flush();
        entityManager.clear();

        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글 내용");

        // when
        CommentDto result = commentService.updateComment(comment.getId(), user1.getId(), request);
        entityManager.flush();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("수정된 댓글 내용");

        // DB 검증
        Comment updated = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo("수정된 댓글 내용");
    }

    @Test
    @DisplayName("updateComment - 댓글이 존재하지 않을 때 예외 발생")
    void updateComment_ThrowsException_WhenCommentNotFound() {
        // given
        UUID fakeCommentId = UUID.randomUUID();
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글");

        // when & then
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
            () -> commentService.updateComment(fakeCommentId, user1.getId(), request)
        );

        assertThat(exception.getMessage()).contains("댓글을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("updateComment - 삭제된 댓글 수정 시도 시 예외 발생")
    void updateComment_ThrowsException_WhenCommentIsDeleted() {
        // given
        Comment comment = commentRepository.save(Comment.builder()
            .article(article1)
            .user(user1)
            .content("삭제될 댓글")
            .build());
        comment.softDelete(); // 논리 삭제
        commentRepository.save(comment);
        entityManager.flush();

        CommentUpdateRequest request = new CommentUpdateRequest("수정 시도");

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> commentService.updateComment(comment.getId(), user1.getId(), request)
        );

        assertThat(exception.getMessage()).contains("삭제된 댓글은 수정할 수 없습니다");
    }

    @Test
    @DisplayName("updateComment - 작성자가 아닌 사용자가 수정 시도 시 예외 발생")
    void updateComment_ThrowsException_WhenUserIsNotOwner() {
        // given
        Comment comment = commentRepository.save(Comment.builder()
            .article(article1)
            .user(user1) // user1이 작성
            .content("원래 댓글")
            .build());
        entityManager.flush();

        CommentUpdateRequest request = new CommentUpdateRequest("수정 시도");

        // when & then: user2가 수정 시도
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> commentService.updateComment(comment.getId(), user2.getId(), request)
        );

        assertThat(exception.getMessage()).contains("본인이 작성한 댓글만 수정할 수 있습니다");
    }

    @Test
    @DisplayName("deleteComment - 논리 삭제 성공")
    void deleteComment_SoftDelete_Success() {
        // given
        Comment comment = commentRepository.save(Comment.builder()
            .article(article1)
            .user(user1)
            .content("삭제할 댓글")
            .build());
        entityManager.flush();
        entityManager.clear();

        // when
        commentService.deleteComment(comment.getId(), user1.getId());
        entityManager.flush();
        entityManager.clear();

        // then
        Comment deleted = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("deleteComment - 이미 삭제된 댓글 삭제 시도 시 예외 발생")
    void deleteComment_ThrowsException_WhenAlreadyDeleted() {
        // given
        Comment comment = commentRepository.save(Comment.builder()
            .article(article1)
            .user(user1)
            .content("이미 삭제된 댓글")
            .build());
        comment.softDelete();
        commentRepository.save(comment);
        entityManager.flush();

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> commentService.deleteComment(comment.getId(), user1.getId())
        );

        assertThat(exception.getMessage()).contains("이미 삭제된 댓글입니다");
    }

    @Test
    @DisplayName("deleteComment - 작성자가 아닌 사용자가 삭제 시도 시 예외 발생")
    void deleteComment_ThrowsException_WhenUserIsNotOwner() {
        // given
        Comment comment = commentRepository.save(Comment.builder()
            .article(article1)
            .user(user1)
            .content("댓글")
            .build());
        entityManager.flush();

        // when & then: user2가 삭제 시도
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> commentService.deleteComment(comment.getId(), user2.getId())
        );

        assertThat(exception.getMessage()).contains("본인이 작성한 댓글만 삭제할 수 있습니다");
    }

    @Test
    @DisplayName("hardDeleteComment - 물리 삭제 성공")
    void hardDeleteComment_Success() {
        // given
        Comment comment = commentRepository.save(Comment.builder()
            .article(article1)
            .user(user1)
            .content("물리 삭제할 댓글")
            .build());
        UUID commentId = comment.getId();
        entityManager.flush();
        entityManager.clear();

        // when
        commentService.hardDeleteComment(commentId);
        entityManager.flush();
        entityManager.clear();

        // then: DB에서 완전히 삭제됨
        assertThat(commentRepository.findById(commentId)).isEmpty();
    }

    @Test
    @DisplayName("hardDeleteComment - 댓글이 존재하지 않을 때 예외 발생")
    void hardDeleteComment_ThrowsException_WhenCommentNotFound() {
        // given
        UUID fakeCommentId = UUID.randomUUID();

        // when & then
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
            () -> commentService.hardDeleteComment(fakeCommentId)
        );

        assertThat(exception.getMessage()).contains("댓글을 찾을 수 없습니다");
    }
}
