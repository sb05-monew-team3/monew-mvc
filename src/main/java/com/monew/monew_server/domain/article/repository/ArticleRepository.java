package com.monew.monew_server.domain.article.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.monew.monew_server.domain.article.entity.Article;

public interface ArticleRepository extends JpaRepository<Article, UUID>, ArticleRepositoryCustom {
	@Modifying
	@Query(value = """
		INSERT INTO articles (id, source, source_url, title, summary, publish_date, created_at, updated_at)
			VALUES (:id, CAST(:source AS source), :sourceUrl, :title, :summary, :publishDate, now(), NULL)
			ON CONFLICT (id) DO NOTHING
		""", nativeQuery = true)
	int insertIfNotExists(
		@Param("id") UUID id,
		@Param("source") String source,
		@Param("sourceUrl") String sourceUrl,
		@Param("title") String title,
		@Param("summary") String summary,
		@Param("publishDate") Instant publishDate
	);
}
