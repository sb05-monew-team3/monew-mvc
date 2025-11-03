package com.monew.monew_server.domain.article.entity;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.monew.monew_server.domain.article.dto.ArticleSaveDto;
import com.monew.monew_server.domain.common.BaseDeletableEntity;
import com.monew.monew_server.domain.interest.entity.ArticleInterest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
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
@Table(name = "articles",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_articles_source_source_url",
		columnNames = {"source", "sourceUrl"}
	))
@Getter
@Setter
@SuperBuilder
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends BaseDeletableEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private ArticleSource source;

	@Column(columnDefinition = "text", nullable = false)
	private String sourceUrl;

	@Column(columnDefinition = "text", nullable = false)
	private String title;

	@Column(columnDefinition = "text", nullable = false)
	private String summary;

	@Column(nullable = false)
	private Instant publishDate;

	@OneToMany(mappedBy = "article")
	private List<ArticleInterest> articleInterests;

	public static Article fromDto(ArticleSaveDto dto) {
		return Article.builder()
			.source(dto.getSource())
			.sourceUrl(dto.getSourceUrl())
			.title(dto.getTitle())
			.summary(dto.getSummary())
			.publishDate(dto.getPublishDate())
			.build();
	}

	public String getOriginalLink() {
		return source + ":" + sourceUrl;
	}
}
