package com.monew.monew_server.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.article.entity.ArticleSource;
import com.monew.monew_server.domain.article.repository.ArticleRepository;
import com.monew.monew_server.domain.comment.dto.CommentLikeDto;
import com.monew.monew_server.domain.comment.entity.Comment;
import com.monew.monew_server.domain.comment.entity.CommentLike;
import com.monew.monew_server.domain.comment.repository.CommentLikeRepository;
import com.monew.monew_server.domain.comment.repository.CommentRepository;
import com.monew.monew_server.domain.notification.entity.Notification;
import com.monew.monew_server.domain.notification.entity.NotificationResourceType;
import com.monew.monew_server.domain.notification.repository.NotificationRepository;
import com.monew.monew_server.domain.user.entity.User;
import com.monew.monew_server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
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
class CommentLikeServiceTest {

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

    @Autowired private CommentLikeService commentLikeService;
    @Autowired private CommentLikeRepository commentLikeRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EntityManager entityManager;

    private User user1;
    private User user2;
    private Article article1;
    private Comment comment1;

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
            .content("테스트 댓글")
            .build()
        );

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("addLike - 좋아요 추가 성공")
    void addLike_Success() {
        // given: comment1에 좋아요가 없는 상태

        // when
        CommentLikeDto result = commentLikeService.addLike(comment1.getId(), user1.getId());
        entityManager.flush();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getLikedBy()).isEqualTo(user1.getId());
        assertThat(result.getCommentId()).isEqualTo(comment1.getId());
        assertThat(result.getArticleId()).isEqualTo(article1.getId());
        assertThat(result.getCommentUserId()).isEqualTo(user1.getId());
        assertThat(result.getCommentUserNickname()).isEqualTo("테스터1");
        assertThat(result.getCommentContent()).isEqualTo("테스트 댓글");
        assertThat(result.getCommentLikeCount()).isEqualTo(1L);

        // DB 검증
        assertThat(commentLikeRepository.findById(result.getId())).isPresent();
        assertThat(commentLikeRepository.countByComment_Id(comment1.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("addLike - 댓글이 존재하지 않을 때 예외 발생")
    void addLike_ThrowsException_WhenCommentNotFound() {
        // given
        UUID fakeCommentId = UUID.randomUUID();

        // when & then
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
            () -> commentLikeService.addLike(fakeCommentId, user1.getId())
        );

        assertThat(exception.getMessage()).contains("댓글을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("addLike - 삭제된 댓글에 좋아요 시도 시 예외 발생")
    void addLike_ThrowsException_WhenCommentIsDeleted() {
        // given: comment1 논리 삭제
        Comment comment = commentRepository.findById(comment1.getId()).orElseThrow();
        comment.softDelete();
        commentRepository.save(comment);
        entityManager.flush();
        entityManager.clear();

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> commentLikeService.addLike(comment1.getId(), user1.getId())
        );

        assertThat(exception.getMessage()).contains("삭제된 댓글에는 좋아요를 누를 수 없습니다");
    }

    @Test
    @DisplayName("addLike - 이미 좋아요를 누른 경우 예외 발생")
    void addLike_ThrowsException_WhenAlreadyLiked() {
        // given: user1이 이미 좋아요를 누른 상태
        commentLikeRepository.save(CommentLike.builder()
            .comment(comment1)
            .user(user1)
            .build()
        );
        entityManager.flush();
        entityManager.clear();

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> commentLikeService.addLike(comment1.getId(), user1.getId())
        );

        assertThat(exception.getMessage()).contains("이미 좋아요를 누른 댓글입니다");
    }

    @Test
    @DisplayName("addLike - 여러 사용자가 좋아요를 누를 수 있음")
    void addLike_MultipleUsers_Success() {
        // given: user1이 먼저 좋아요
        commentLikeService.addLike(comment1.getId(), user1.getId());
        entityManager.flush();

        // when: user2도 좋아요
        CommentLikeDto result = commentLikeService.addLike(comment1.getId(), user2.getId());
        entityManager.flush();

        // then
        assertThat(result.getLikedBy()).isEqualTo(user2.getId());
        assertThat(result.getCommentLikeCount()).isEqualTo(2L);

        // DB 검증
        assertThat(commentLikeRepository.countByComment_Id(comment1.getId())).isEqualTo(2L);
    }

    @Test
    @DisplayName("removeLike - 좋아요 취소 성공")
    void removeLike_Success() {
        // given: user1이 좋아요를 누른 상태
        CommentLike like = commentLikeRepository.save(CommentLike.builder()
            .comment(comment1)
            .user(user1)
            .build()
        );
        UUID likeId = like.getId();
        entityManager.flush();
        entityManager.clear();

        // 사전 검증: 좋아요가 존재함
        assertThat(commentLikeRepository.findById(likeId)).isPresent();

        // when
        commentLikeService.removeLike(comment1.getId(), user1.getId());
        entityManager.flush();
        entityManager.clear();

        // then: DB에서 삭제됨
        assertThat(commentLikeRepository.findById(likeId)).isEmpty();
        assertThat(commentLikeRepository.countByComment_Id(comment1.getId())).isEqualTo(0L);
    }

    @Test
    @DisplayName("removeLike - 좋아요가 존재하지 않을 때 예외 발생")
    void removeLike_ThrowsException_WhenLikeNotFound() {
        // given: user1이 좋아요를 누르지 않은 상태

        // when & then
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
            () -> commentLikeService.removeLike(comment1.getId(), user1.getId())
        );

        assertThat(exception.getMessage()).contains("좋아요를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("removeLike - 다른 사용자의 좋아요는 취소할 수 없음")
    void removeLike_ThrowsException_WhenDifferentUser() {
        // given: user1이 좋아요를 누름
        commentLikeRepository.save(CommentLike.builder()
            .comment(comment1)
            .user(user1)
            .build()
        );
        entityManager.flush();

        // when & then: user2가 user1의 좋아요를 취소 시도
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
            () -> commentLikeService.removeLike(comment1.getId(), user2.getId())
        );

        assertThat(exception.getMessage()).contains("좋아요를 찾을 수 없습니다");

        // DB 검증: user1의 좋아요는 여전히 존재
        assertThat(commentLikeRepository.countByComment_Id(comment1.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("addLike - 다른 사용자 댓글에 좋아요 시 알림 생성")
    void addLike_CreatesNotification_WhenDifferentUser() {
        // given: user2가 작성한 댓글
        Comment comment = commentRepository.save(Comment.builder()
            .article(article1)
            .user(user2)  // user2의 댓글
            .content("user2의 댓글")
            .build());
        entityManager.flush();
        entityManager.clear();

        // when: user1이 좋아요
        CommentLikeDto result = commentLikeService.addLike(comment.getId(), user1.getId());
        entityManager.flush();

        // then: user2에게 알림이 생성됨
        long notificationCount = notificationRepository
            .countByUserIdAndConfirmedFalse(user2.getId());
        assertThat(notificationCount).isEqualTo(1L);

        // 알림 내용 검증
        List<Notification> notifications = notificationRepository
            .findUnconfirmedWithCursor(user2.getId(), null, null, 10);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getContent())
            .isEqualTo("테스터1님이 나의 댓글을 좋아합니다.");
        assertThat(notifications.get(0).getResourceType())
            .isEqualTo(NotificationResourceType.COMMENT);
        assertThat(notifications.get(0).getResourceId())
            .isEqualTo(comment.getId());
    }

    @Test
    @DisplayName("addLike - 본인 댓글에 좋아요 시 알림 생성 안 됨")
    void addLike_DoesNotCreateNotification_WhenSameUser() {
        // given: user1이 작성한 댓글 (setUp()에서 생성된 comment1)
        entityManager.clear();

        // when: user1이 본인 댓글에 좋아요
        CommentLikeDto result = commentLikeService.addLike(comment1.getId(), user1.getId());
        entityManager.flush();

        // then: 알림 생성 안 됨
        long notificationCount = notificationRepository
            .countByUserIdAndConfirmedFalse(user1.getId());
        assertThat(notificationCount).isEqualTo(0L);
    }
}
