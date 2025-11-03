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

	public static boolean isValid(String s) {
		try {
			valueOf(s);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
