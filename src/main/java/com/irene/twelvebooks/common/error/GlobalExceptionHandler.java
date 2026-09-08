package com.irene.twelvebooks.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus())
				.body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
				.map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
				.toList();
		ErrorCode errorCode = ErrorCode.INVALID_INPUT;
		return ResponseEntity.status(errorCode.getStatus())
				.body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), fieldErrors));
	}

	/**
	 * {@code @RequestParam}·{@code @PathVariable}에 걸린 제약이 깨진 경우. 본문 검증과 달리
	 * 이쪽은 {@link HandlerMethodValidationException}으로 오기 때문에 따로 받지 않으면
	 * catch-all로 흘러 <b>클라이언트 실수가 500</b>이 된다.
	 */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException e) {
		List<ErrorResponse.FieldError> fieldErrors = e.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> new ErrorResponse.FieldError(
								result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
				.toList();
		ErrorCode errorCode = ErrorCode.INVALID_INPUT;
		return ResponseEntity.status(errorCode.getStatus())
				.body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), fieldErrors));
	}

	/**
	 * 파라미터를 아예 안 보냈거나({@code q} 누락), 보낸 값을 선언한 타입으로 바꿀 수 없는 경우
	 * ({@code page=abc}, {@code /books/not-a-number}). 제약 위반과 달리 이쪽은 검증까지 가지도
	 * 못하고 바인딩에서 끝나므로 예외 타입이 다르다 — 따로 받지 않으면 catch-all로 흘러 500이 된다.
	 *
	 * <p>어떤 타입을 기대했는지는 응답에 싣지 않는다. 필드 이름까지가 클라이언트가 고칠 수 있는
	 * 정보이고, 그 뒤는 내부 구조다.
	 */
	@ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErrorResponse> handleUnbindableParameter(Exception e) {
		String field = e instanceof MissingServletRequestParameterException missing
				? missing.getParameterName()
				: ((MethodArgumentTypeMismatchException) e).getName();
		ErrorCode errorCode = ErrorCode.INVALID_INPUT;
		return ResponseEntity.status(errorCode.getStatus())
				.body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(),
						List.of(new ErrorResponse.FieldError(field, "값을 확인해 주세요."))));
	}

	/**
	 * 본문이 JSON으로 읽히지 않는 경우. 잘못 보낸 쪽의 문제이므로 400이다.
	 * catch-all로 흘려보내면 클라이언트 실수 하나가 500과 스택트레이스 로그를 남긴다.
	 * 파싱 실패 사유는 내부 구조를 드러내므로 응답에 싣지 않는다.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
		log.debug("본문을 읽지 못했습니다", e);
		ErrorCode errorCode = ErrorCode.INVALID_INPUT;
		return ResponseEntity.status(errorCode.getStatus())
				.body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of()));
	}

	/**
	 * 마지막 방어선. 원인은 서버 로그에만 남기고, 응답에는 내부 메시지나 스택트레이스를 절대 싣지 않는다.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
		return ResponseEntity.status(errorCode.getStatus())
				.body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of()));
	}
}
