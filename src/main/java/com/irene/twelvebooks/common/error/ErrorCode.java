package com.irene.twelvebooks.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "요청 값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A002", "이메일 또는 비밀번호가 올바르지 않습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "다시 로그인해 주세요."),
	INVALID_RESET_TOKEN(HttpStatus.UNAUTHORIZED, "A005",
			"링크가 만료되었거나 이미 사용되었습니다. 다시 요청해 주세요."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "U001", "이미 사용 중인 이메일입니다."),
	HANDLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "U002", "이미 사용 중인 handle입니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U003", "사용자를 찾을 수 없습니다."),
	BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "책을 찾을 수 없습니다."),
	BOOK_SIGNATURE_MISMATCH(HttpStatus.BAD_REQUEST, "B002", "검색 결과를 그대로 등록해 주세요."),
	READING_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "서재에서 찾을 수 없습니다."),
	READING_ALREADY_EXISTS(HttpStatus.CONFLICT, "R002", "이미 서재에 있는 책입니다."),
	REREAD_CHOICE_REQUIRED(HttpStatus.CONFLICT, "R004",
			"완독한 기록입니다. 잘못 기록한 것을 고치려면 reread=false, 다시 읽기 시작이면 reread=true로 보내세요."),
	PREVIOUS_READING_EXISTS(HttpStatus.CONFLICT, "R003",
			"전에 읽던 기록이 있습니다. 이어서 읽을지 새로 시작할지 선택해 주세요."),
	POST_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "감상평을 찾을 수 없습니다."),
	ALREADY_LIKED(HttpStatus.CONFLICT, "P002", "이미 좋아요를 눌렀습니다."),
	COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P003", "댓글을 찾을 수 없습니다."),
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다."),
	REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "신고를 찾을 수 없습니다."),
	ALREADY_REPORTED(HttpStatus.CONFLICT, "S002", "이미 신고한 대상입니다."),
	SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "S003", "자기 자신은 신고할 수 없습니다."),
	RESTORE_CHOICE_REQUIRED(HttpStatus.CONFLICT, "S004",
			"기각하면서 대상을 다시 공개할지 함께 정해야 합니다. restore를 보내세요."),
	OTHER_ACTIONED_REPORTS_REMAIN(HttpStatus.CONFLICT, "S005",
			"이 대상에 인정된 신고가 남아 있습니다. 그것부터 처리한 뒤 공개하세요."),
	SELF_FOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "F001", "자기 자신은 팔로우할 수 없습니다."),
	ALREADY_FOLLOWING(HttpStatus.CONFLICT, "F002", "이미 팔로우하고 있습니다."),
	SELF_BLOCK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "B001", "자기 자신은 차단할 수 없습니다."),
	ALREADY_BLOCKED(HttpStatus.CONFLICT, "B002", "이미 차단한 사용자입니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "A004", "권한이 없습니다."),
	EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "E001", "외부 서비스를 이용할 수 없습니다."),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "C003",
			"요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),
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
