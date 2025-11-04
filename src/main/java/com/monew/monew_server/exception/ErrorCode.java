package com.monew.monew_server.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	INVALID_INPUT_VALUE("잘못된 입력값입니다."),
	INTERNAL_SERVER_ERROR("서버 내부 오류가 발생했습니다."),
	INVALID_REQUEST("잘못된 요청입니다."),
	ARTICLE_NOT_FOUND("기사를 찾을 수 없습니다."),
	INTEREST_NOT_FOUND("관심사를 찾을 수 없습니다."),
	INTEREST_NAME_DUPLICATION("유사한 이름의 관심사가 이미 존재합니다."),
	NOTIFICATION_NOT_FOUND("알림을 찾을 수 없습니다."),
	USER_NOT_FOUND("사용자를 찾을 수 없습니다.");

	private final String message;
}
