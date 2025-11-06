-- CREATE DATABASE monew WITH OWNER postgres ENCODING 'utf8';

DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS articles CASCADE;
DROP TABLE IF EXISTS article_views CASCADE;
DROP TABLE IF EXISTS interests CASCADE;
DROP TABLE IF EXISTS interest_keywords CASCADE;
DROP TABLE IF EXISTS article_interests CASCADE;
DROP TABLE IF EXISTS subscriptions CASCADE;
DROP TABLE IF EXISTS comments CASCADE;
DROP TABLE IF EXISTS comment_likes CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;

/* 사용자 */
CREATE TABLE users
(
    id         UUID PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    nickname   VARCHAR(30)  NOT NULL,
    password   VARCHAR(60)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ           DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
COMMENT ON TABLE users IS '사용자';
COMMENT ON COLUMN users.id IS '사용자 ID';
COMMENT ON COLUMN users.email IS '이메일';
COMMENT ON COLUMN users.nickname IS '닉네임';
COMMENT ON COLUMN users.password IS '비밀번호';
COMMENT ON COLUMN users.created_at IS '가입 일자';
COMMENT ON COLUMN users.updated_at IS '수정 일자';
COMMENT ON COLUMN users.deleted_at IS '소프트 삭제 일자';

/* 기사 */
DROP TYPE IF EXISTS SOURCE;
CREATE TYPE SOURCE AS ENUM ('NAVER', 'HANKYUNG', 'CHOSUN', 'YEONHAP');
CREATE TABLE articles
(
    id           UUID PRIMARY KEY,
    source       SOURCE      NOT NULL,
    source_url   TEXT        NOT NULL,
    title        TEXT        NOT NULL,
    publish_date TIMESTAMPTZ NOT NULL,
    summary      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ          DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT uk_articles_source_source_url UNIQUE (source, source_url)
);
COMMENT ON TABLE articles IS '뉴스 기사';
COMMENT ON COLUMN articles.id IS '기사 ID';
COMMENT ON COLUMN articles.source IS '출처';
COMMENT ON COLUMN articles.source_url IS '원본 기사 URL';
COMMENT ON COLUMN articles.title IS '제목';
COMMENT ON COLUMN articles.publish_date IS '날짜';
COMMENT ON COLUMN articles.summary IS '요약';
COMMENT ON COLUMN articles.created_at IS '등록 일자';
COMMENT ON COLUMN articles.updated_at IS '수정 일자';
COMMENT ON COLUMN articles.deleted_at IS '소프트 삭제 일자';

/* 기사 조회 */
CREATE TABLE article_views
(
    id         UUID PRIMARY KEY,
    article_id UUID        NOT NULL,
    user_id    UUID        NOT NULL, -- viewed_by
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_article_views_article_id_user_id UNIQUE (article_id, user_id)
);
ALTER TABLE article_views
    ADD CONSTRAINT fk_article_views_articles_id FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE;
ALTER TABLE article_views
    ADD CONSTRAINT fk_article_views_users_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
COMMENT ON TABLE article_views IS '기사 조회';
COMMENT ON COLUMN article_views.id IS '기사 조회 ID';
COMMENT ON COLUMN article_views.article_id IS '기사 ID';
COMMENT ON COLUMN article_views.user_id IS '기사를 조회한 사용자 ID';
COMMENT ON COLUMN article_views.created_at IS '조회 일자';

/* 관심사 */
CREATE TABLE interests
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ           DEFAULT NOW()
);
COMMENT ON TABLE interests IS '관심사';
COMMENT ON COLUMN interests.id IS '관심사 ID';
COMMENT ON COLUMN interests.name IS '관심사 이름';
COMMENT ON COLUMN interests.created_at IS '등록 일자';
COMMENT ON COLUMN interests.updated_at IS '수정 일자';

/* 관심사 키워드 */
CREATE TABLE interest_keywords
(
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    interest_id UUID         NOT NULL
);
ALTER TABLE interest_keywords
    ADD CONSTRAINT fk_interest_keywords_interests_id FOREIGN KEY (interest_id) REFERENCES interests (id) ON DELETE CASCADE;
COMMENT ON TABLE interest_keywords IS '관련 키워드';
COMMENT ON COLUMN interest_keywords.id IS '키워드 ID';
COMMENT ON COLUMN interest_keywords.name IS '키워드 이름';
COMMENT ON COLUMN interest_keywords.interest_id IS '관심사 ID';

/* 기사 - 관심사 */
CREATE TABLE article_interests
(
    id          UUID PRIMARY KEY,
    article_id  UUID        NOT NULL,
    interest_id UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_article_interests_article_id_interest_id UNIQUE (article_id, interest_id)
);
ALTER TABLE article_interests
    ADD CONSTRAINT fk_article_interests_article_id FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE;
ALTER TABLE article_interests
    ADD CONSTRAINT fk_article_interests_interests_id FOREIGN KEY (interest_id) REFERENCES interests (id) ON DELETE CASCADE;

/* 구독 */
CREATE TABLE subscriptions
(
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL,
    interest_id UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_subscriptions_user_id_interest_id UNIQUE (user_id, interest_id)
);
ALTER TABLE subscriptions
    ADD CONSTRAINT fk_subscriptions_interests_id FOREIGN KEY (interest_id) REFERENCES interests (id) ON DELETE CASCADE;
ALTER TABLE subscriptions
    ADD CONSTRAINT fk_subscriptions_users_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
COMMENT ON TABLE subscriptions IS '구독 정보';
COMMENT ON COLUMN subscriptions.id IS '구독 정보 ID';
COMMENT ON COLUMN subscriptions.interest_id IS '관심사 ID';
COMMENT ON COLUMN subscriptions.user_id IS '구독한 사용자 ID';
COMMENT ON COLUMN subscriptions.created_at IS '구독 일자';

/* 댓글 */
CREATE TABLE comments
(
    id         UUID PRIMARY KEY,
    article_id UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ          DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
ALTER TABLE comments
    ADD CONSTRAINT fk_subscriptions_articles_id FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE;
ALTER TABLE comments
    ADD CONSTRAINT fk_subscriptions_users_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
COMMENT ON TABLE comments IS '댓글';
COMMENT ON COLUMN comments.id IS '댓글 ID';
COMMENT ON COLUMN comments.content IS '내용';
COMMENT ON COLUMN comments.article_id IS '기사 ID';
COMMENT ON COLUMN comments.user_id IS '작성자 ID';
COMMENT ON COLUMN comments.created_at IS '작성 일자';
COMMENT ON COLUMN comments.updated_at IS '수정 일자';
COMMENT ON COLUMN comments.deleted_at IS '소프트 삭제 일자';

/* 댓글 좋아요 */
CREATE TABLE comment_likes
(
    id         UUID PRIMARY KEY,
    comment_id UUID        NOT NULL,
    user_id    UUID        NOT NULL, -- liked_by
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_comment_likes_comment_id_user_id UNIQUE (comment_id, user_id)

);
ALTER TABLE comment_likes
    ADD CONSTRAINT fk_comment_likes_comments_id FOREIGN KEY (comment_id) REFERENCES comments (id) ON DELETE CASCADE;
ALTER TABLE comment_likes
    ADD CONSTRAINT fk_comment_likes_users_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
COMMENT ON TABLE comment_likes IS '좋아요';
COMMENT ON COLUMN comment_likes.id IS '좋아요 ID';
COMMENT ON COLUMN comment_likes.user_id IS '좋아요한 사용자 ID';
COMMENT ON COLUMN comment_likes.comment_id IS '댓글 ID';
COMMENT ON COLUMN comment_likes.created_at IS '등록 일자';

/* 알림 */
DROP TYPE IF EXISTS RESOURCE_TYPE;
CREATE TYPE RESOURCE_TYPE AS ENUM ('interest', 'comment');
CREATE TABLE notifications
(
    id            UUID PRIMARY KEY,
    confirmed     BOOLEAN       NOT NULL DEFAULT FALSE,
    user_id       UUID          NOT NULL,
    content       TEXT          NOT NULL,
    resource_type RESOURCE_TYPE NOT NULL,
    resource_id   UUID          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ            DEFAULT NOW()
);
ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_users_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
COMMENT ON TABLE notifications IS '알림';
COMMENT ON COLUMN notifications.id IS '알림 ID';
COMMENT ON COLUMN notifications.confirmed IS '확인 여부';
COMMENT ON COLUMN notifications.user_id IS '사용자 ID';
COMMENT ON COLUMN notifications.content IS '내용';
COMMENT ON COLUMN notifications.resource_type IS '리소스 유형';
COMMENT ON COLUMN notifications.resource_id IS '리소스 ID';
COMMENT ON COLUMN notifications.created_at IS '생성 일자';
COMMENT ON COLUMN notifications.updated_at IS '확인 일자';