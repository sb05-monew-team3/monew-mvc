-- ============================================================
-- 인덱스 성능/용량 비교 스크립트
-- ============================================================
--
-- 1️⃣ 인덱스 제거
DROP INDEX IF EXISTS idx_articles_publish_date_desc;
DROP INDEX IF EXISTS idx_articles_source_publish_date_desc;
DROP INDEX IF EXISTS idx_articles_title_trgm;
DROP INDEX IF EXISTS idx_articles_summary_trgm;
DROP INDEX IF EXISTS idx_article_views_article_id;
DROP INDEX IF EXISTS idx_article_views_user_id;
DROP INDEX IF EXISTS idx_comments_article_id;
DROP INDEX IF EXISTS idx_comments_article_id_created_at_desc;
DROP INDEX IF EXISTS idx_comment_likes_comment_id;
DROP INDEX IF EXISTS idx_comment_likes_user_id;
DROP INDEX IF EXISTS idx_subscriptions_user_id;
DROP INDEX IF EXISTS idx_subscriptions_interest_id;
DROP INDEX IF EXISTS idx_article_interests_interest_id;
DROP INDEX IF EXISTS idx_notifications_user_id_created_at_desc;

DROP EXTENSION IF EXISTS pg_trgm;
--
-- ============================================================
-- 2️⃣ 인덱스 제거 후 용량 확인
-- ============================================================
SELECT '== [INDEX BEFORE] ==' AS section;

SELECT
    relname AS table_name,
    pg_size_pretty(pg_relation_size(relid)) AS table_size,
    pg_size_pretty(pg_indexes_size(relid)) AS index_size,
    pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;

SELECT '총 용량(인덱스 제거 후)' AS label,
       pg_size_pretty(SUM(pg_total_relation_size(relid))) AS total_size
FROM pg_catalog.pg_statio_user_tables;
--
-- ============================================================
-- 3️⃣ 인덱스 재생성
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_articles_publish_date_desc
   ON articles (publish_date DESC);

CREATE INDEX IF NOT EXISTS idx_articles_source_publish_date_desc
   ON articles (source, publish_date DESC);

CREATE INDEX IF NOT EXISTS idx_articles_title_trgm
   ON articles USING GIN (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_articles_summary_trgm
   ON articles USING GIN (summary gin_trgm_ops);


CREATE INDEX IF NOT EXISTS idx_article_views_article_id
   ON article_views (article_id);

CREATE INDEX IF NOT EXISTS idx_article_views_user_id
   ON article_views (user_id);


CREATE INDEX IF NOT EXISTS idx_comments_article_id
   ON comments (article_id);

CREATE INDEX IF NOT EXISTS idx_comments_article_id_created_at_desc
   ON comments (article_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_comment_likes_comment_id
   ON comment_likes (comment_id);

CREATE INDEX IF NOT EXISTS idx_comment_likes_user_id
   ON comment_likes (user_id);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id
   ON subscriptions (user_id);

CREATE INDEX IF NOT EXISTS idx_subscriptions_interest_id
   ON subscriptions (interest_id);

CREATE INDEX IF NOT EXISTS idx_article_interests_interest_id
   ON article_interests (interest_id);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id_created_at_desc
   ON notifications (user_id, created_at DESC);

--
SELECT '== [INDEX AFTER] ==' AS section;
--
--SELECT
--    relname AS table_name,
--    pg_size_pretty(pg_relation_size(relid)) AS table_size,
--    pg_size_pretty(pg_indexes_size(relid)) AS index_size,
--    pg_size_pretty(pg_total_relation_size(relid)) AS total_size
--FROM pg_catalog.pg_statio_user_tables
--ORDER BY pg_total_relation_size(relid) DESC;
--
--SELECT '총 용량(인덱스 생성 후)' AS label,
--       pg_size_pretty(SUM(pg_total_relation_size(relid))) AS total_size
--FROM pg_catalog.pg_statio_user_tables;
