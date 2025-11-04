package com.monew.monew_server.exception;

import java.util.Map;

public class UserActivityException extends BaseException {

    public UserActivityException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode);
        details.forEach(this::addDetail);
    }

    public UserActivityException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }
}
