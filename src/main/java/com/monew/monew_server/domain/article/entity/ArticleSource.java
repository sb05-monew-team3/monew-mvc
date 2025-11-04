package com.monew.monew_server.domain.article.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ArticleSource {
	NAVER,
	HANKYUNG,
	CHOSUN,
	YEONHAP;

	public static boolean isValid(String name) {
		try {
			valueOf(name.toUpperCase());
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
