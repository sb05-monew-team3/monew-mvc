package com.monew.monew_server.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends BaseException {

	public NotFoundException(ErrorCode errorCode) {
    	super(errorCode);
	}
}
