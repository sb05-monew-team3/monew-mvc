package com.monew.monew_server.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception exception) {
		log.error("예상 못한 예외 발생: {}", exception.getMessage(), exception);

		ErrorResponse errorResponse = new ErrorResponse(exception, 500);
		return ResponseEntity.status(errorResponse.status()).body(errorResponse);
	}

	@ExceptionHandler(BaseException.class)
	public ResponseEntity<ErrorResponse> handleCustomException(BaseException exception) {
		log.error("개발자 정의 예외 발생: code={}, message={}", exception.getErrorCode(), exception.getMessage());

		HttpStatus httpStatus = determineHttpStatus(exception);
		ErrorResponse errorResponse = new ErrorResponse(exception, httpStatus.value());
		return ResponseEntity.status(httpStatus).body(errorResponse);
	}

	private HttpStatus determineHttpStatus(BaseException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		return switch (errorCode) {
			case INVALID_REQUEST, INVALID_INPUT_VALUE -> HttpStatus.BAD_REQUEST;
			case ARTICLE_NOT_FOUND,
				 INTEREST_NOT_FOUND,
				 NOTIFICATION_NOT_FOUND,
				 USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case INTEREST_NAME_DUPLICATION -> HttpStatus.CONFLICT;
			case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
		log.error("요청 유효성 검사 실패: {}", ex.getMessage());

		Map<String, Object> details = new HashMap<>();
		ex.getBindingResult().getAllErrors().forEach(error -> {
			String fieldName = ((FieldError)error).getField();
			String errorMessage = error.getDefaultMessage();
			details.put(fieldName, errorMessage);
		});

		ErrorResponse response = new ErrorResponse(
			Instant.now(),
			"INVALID_INPUT_VALUE",
			"잘못된 입력값입니다.",
			details,
			"DomainException",
			HttpStatus.BAD_REQUEST.value()
		);

		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(response);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleMissingHeader(
		MissingRequestHeaderException exception,
		HttpServletRequest request
	) {
		log.warn(
			"Required header '{}' is missing for request: {} {}",
			exception.getHeaderName(),
			request.getMethod(),
			request.getRequestURI()
		);
		return new ErrorResponse(exception, HttpStatus.BAD_REQUEST.value());
	}
}
