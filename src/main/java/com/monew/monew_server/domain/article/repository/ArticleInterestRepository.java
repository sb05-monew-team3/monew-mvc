package com.monew.monew_server.domain.article.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.monew.monew_server.domain.interest.entity.ArticleInterest;

public interface ArticleInterestRepository extends JpaRepository<ArticleInterest, UUID> {

}