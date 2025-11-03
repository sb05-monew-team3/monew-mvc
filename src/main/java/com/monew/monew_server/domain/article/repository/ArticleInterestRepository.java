package com.monew.monew_server.domain.article.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monew.monew_server.domain.interest.entity.ArticleInterest;

@Repository
public interface ArticleInterestRepository extends JpaRepository<ArticleInterest, UUID> {

}