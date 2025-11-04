package com.monew.monew_server.domain.user_activity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.article.entity.ArticleView;

@Repository
public interface UserActivityArticleViewRepository extends JpaRepository<ArticleView, UUID> {
	// 특정 사용자가 본 뉴스 기사
	@EntityGraph(attributePaths = {"article"})
	List<ArticleView> findTop10ByUser_IdOrderByCreatedAtDesc(UUID userId);

	long countByArticle_id(UUID articleId);
}
