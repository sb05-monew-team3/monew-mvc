package com.monew.monew_server.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {
	private final Instant timestamp;
	private final ErrorCode errorCode;
	private final Map<String, Object> details;

	public BaseException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.timestamp = Instant.now();
		this.errorCode = errorCode;
		this.details = new HashMap<>();
	}

	public void addDetail(String key, Object value) {
		this.details.put(key, value);
	}
}
