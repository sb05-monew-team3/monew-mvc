-- =====================================================
-- MONEW 더미데이터 - 소형 버전
-- 실행 전: CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- =====================================================

-- 0. 초기화
TRUNCATE TABLE
    comment_likes,
    comments,
    notifications,
    subscriptions,
    article_interests,
    interest_keywords,
    articles,
    interests,
    users
RESTART IDENTITY CASCADE;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

--------------------------------------------------------
-- 1. 기본 데이터
--------------------------------------------------------

-- 1) USERS (2,000명)
INSERT INTO users (id, email, nickname, password)
SELECT uuid_generate_v4(),
       'user' || i || '@example.com',
       'user' || i,
       'password'
FROM generate_series(1, 2000) AS s(i);

-- 2) INTERESTS (30개)
INSERT INTO interests (id, name)
SELECT uuid_generate_v4(), 'interest_' || i
FROM generate_series(1, 30) AS s(i);

-- 3) INTEREST_KEYWORDS (200개)
INSERT INTO interest_keywords (id, name, interest_id)
SELECT uuid_generate_v4(),
       'keyword_' || i,
       (SELECT id FROM interests ORDER BY id OFFSET (i % 30) LIMIT 1)
FROM generate_series(1, 200) AS s(i);

-- 4) ARTICLES (10,000개)
INSERT INTO articles (id, source, source_url, title, publish_date, summary)
SELECT uuid_generate_v4(),
       (ARRAY['NAVER','HANKYUNG','CHOSUN','YEONHAP'])[floor(random()*4 + 1)],
       'https://example.com/article_' || i,
       'Article title ' || i,
       NOW() - (i || ' minutes')::interval,
       'Summary text for article ' || i
FROM generate_series(1, 10000) AS s(i);

-- 5) COMMENTS (20,000개)
INSERT INTO comments (id, article_id, user_id, content)
SELECT uuid_generate_v4(),
       (SELECT id FROM articles ORDER BY id OFFSET (i % 10000) LIMIT 1),
       (SELECT id FROM users    ORDER BY id OFFSET (i % 2000)  LIMIT 1),
       'This is comment number ' || i
FROM generate_series(1, 20000) AS s(i);

--------------------------------------------------------
-- 2. 관계 테이블
--------------------------------------------------------

-- 6) ARTICLE_INTERESTS (20,000개)
WITH
    article_ids  AS (SELECT array_agg(id ORDER BY id) AS ids FROM articles),
    interest_ids AS (SELECT array_agg(id ORDER BY id) AS ids FROM interests)
INSERT INTO article_interests (id, article_id, interest_id)
SELECT uuid_generate_v4(),
       article_ids.ids[1 + (g.i - 1) % array_length(article_ids.ids, 1)],
    interest_ids.ids[1 + (g.i - 1) % array_length(interest_ids.ids, 1)]
FROM generate_series(1, 20000) AS g(i),
    article_ids,
    interest_ids
ON CONFLICT (article_id, interest_id) DO NOTHING;

-- 7) SUBSCRIPTIONS (15,000개)
WITH
    user_ids     AS (SELECT array_agg(id ORDER BY id) AS ids FROM users),
    interest_ids AS (SELECT array_agg(id ORDER BY id) AS ids FROM interests)
INSERT INTO subscriptions (id, user_id, interest_id)
SELECT uuid_generate_v4(),
       user_ids.ids[1 + (g.i - 1) % array_length(user_ids.ids, 1)],
    interest_ids.ids[1 + (g.i - 1) % array_length(interest_ids.ids, 1)]
FROM generate_series(1, 15000) AS g(i),
    user_ids,
    interest_ids
ON CONFLICT (user_id, interest_id) DO NOTHING;

-- 8) COMMENT_LIKES (20,000개)
WITH
    comment_ids AS (SELECT array_agg(id ORDER BY id) AS ids FROM comments),
    user_ids    AS (SELECT array_agg(id ORDER BY id) AS ids FROM users)
INSERT INTO comment_likes (id, comment_id, user_id)
SELECT uuid_generate_v4(),
       comment_ids.ids[1 + (g.i - 1) % array_length(comment_ids.ids, 1)],
    user_ids.ids[1 + (g.i - 1) % array_length(user_ids.ids, 1)]
FROM generate_series(1, 20000) AS g(i),
    comment_ids,
    user_ids
ON CONFLICT (comment_id, user_id) DO NOTHING;

-- 9) NOTIFICATIONS (10,000개)
-- resource_type가 ENUM이면 아래 줄 캐스팅해서 쓰세요.
WITH
    user_ids    AS (SELECT array_agg(id ORDER BY id) AS ids FROM users),
    article_ids AS (SELECT array_agg(id ORDER BY id) AS ids FROM articles)
INSERT INTO notifications (id, confirmed, user_id, content, resource_type, resource_id)
SELECT uuid_generate_v4(),
       (random() > 0.5),
       user_ids.ids[1 + (g.i - 1) % array_length(user_ids.ids, 1)],
    'Notification message ' || g.i,
       -- ENUM일 경우:
       -- ((ARRAY['interest','comment'])[floor(random()*2 + 1)])::resource_type,
    (ARRAY['interest','comment'])[floor(random()*2 + 1)],
    article_ids.ids[1 + (g.i - 1) % array_length(article_ids.ids, 1)]
FROM generate_series(1, 10000) AS g(i),
    user_ids,
    article_ids;

--------------------------------------------------------
-- 3. 결과 확인
--------------------------------------------------------
SELECT
    (SELECT count(*) FROM users)             AS users_count,
    (SELECT count(*) FROM interests)         AS interests_count,
    (SELECT count(*) FROM interest_keywords) AS keywords_count,
    (SELECT count(*) FROM articles)          AS articles_count,
    (SELECT count(*) FROM article_interests) AS article_interests_count,
    (SELECT count(*) FROM subscriptions)     AS subscriptions_count,
    (SELECT count(*) FROM comments)          AS comments_count,
    (SELECT count(*) FROM comment_likes)     AS comment_likes_count,
    (SELECT count(*) FROM notifications)     AS notifications_count;
