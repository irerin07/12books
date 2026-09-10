package com.irene.twelvebooks.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "요청 값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A002", "이메일 또는 비밀번호가 올바르지 않습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "다시 로그인해 주세요."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "U001", "이미 사용 중인 이메일입니다."),
	HANDLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "U002", "이미 사용 중인 handle입니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U003", "사용자를 찾을 수 없습니다."),
	BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "책을 찾을 수 없습니다."),
	BOOK_SIGNATURE_MISMATCH(HttpStatus.BAD_REQUEST, "B002", "검색 결과를 그대로 등록해 주세요."),
	READING_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "서재에서 찾을 수 없습니다."),
	READING_ALREADY_EXISTS(HttpStatus.CONFLICT, "R002", "이미 서재에 있는 책입니다."),
	POST_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "감상평을 찾을 수 없습니다."),
	SELF_FOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "F001", "자기 자신은 팔로우할 수 없습니다."),
	ALREADY_FOLLOWING(HttpStatus.CONFLICT, "F002", "이미 팔로우하고 있습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "A004", "권한이 없습니다."),
	EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "E001", "외부 서비스를 이용할 수 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	ErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}
}
