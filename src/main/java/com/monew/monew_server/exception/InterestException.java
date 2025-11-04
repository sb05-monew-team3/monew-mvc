package com.monew.monew_server.exception;

import java.util.Map;

public class InterestException extends BaseException {

    public InterestException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode);
        details.forEach(this::addDetail);
    }

    public InterestException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }
}
