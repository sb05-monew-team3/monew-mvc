package com.monew.monew_server.domain.user_activity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.monew.monew_server.domain.article.entity.ArticleView;

public interface UserActivityArticleViewRepository extends JpaRepository<ArticleView, UUID> {
	// 특정 사용자가 본 뉴스 기사
	List<ArticleView> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
