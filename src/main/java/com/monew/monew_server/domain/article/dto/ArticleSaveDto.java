package com.monew.monew_server.domain.article.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.monew.monew_server.domain.article.entity.ArticleSource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleSaveDto {
	private UUID id;
	private String title;
	private String summary;
	private ArticleSource source;
	private String sourceUrl;
	private Instant publishDate;

	@JsonIgnore
	public String getOriginalLink() {
		return source + ":" + sourceUrl;
	}
}
