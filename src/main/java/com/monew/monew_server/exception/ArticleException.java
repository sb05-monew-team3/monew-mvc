package com.monew.monew_server.exception;

public class ArticleException extends BaseException {

	public ArticleException() {
		super(ErrorCode.ARTICLE_NOT_FOUND);
	}

}