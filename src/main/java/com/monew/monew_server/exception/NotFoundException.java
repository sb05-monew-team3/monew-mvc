package com.monew.monew_server.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {

	private final ErrorCode errorCode;

	public NotFoundException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
}
