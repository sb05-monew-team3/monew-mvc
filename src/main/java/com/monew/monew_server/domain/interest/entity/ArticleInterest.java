package com.monew.monew_server.domain.interest.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.monew.monew_server.domain.article.entity.Article;
import com.monew.monew_server.domain.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "article_interests",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_article_interests_article_id_interest_id",
		columnNames = {"article_id", "interest_id"}
	))
@Getter
@Setter
@SuperBuilder
@ToString(callSuper = true, exclude = {"article"})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleInterest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "article_id")
	private Article article;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "interest_id")
	private Interest interest;

	public static ArticleInterest of(Article article, Interest interest) {
		return ArticleInterest.builder()
			.article(article)
			.interest(interest)
			.build();
	}
}
