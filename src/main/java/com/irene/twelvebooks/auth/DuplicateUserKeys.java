package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/**
 * 사전 조회를 함께 통과한 동시 가입은 유니크 제약에서 갈린다. 어느 제약이 터졌는지에 따라
 * 안내가 달라야 한다 — handle 경합에 "이메일을 바꾸라"고 답하면 사용자는 고칠 수가 없다.
 */
public final class DuplicateUserKeys {

	// 스키마를 따라간다. 탈퇴가 들어오면서 유일성이 살아 있는 계정에만 걸리도록 생성 컬럼으로
	// 옮겼고(V13) 이름도 함께 바뀌었다. 여기를 안 맞추면 handle 경합이 "이메일 중복"으로
	// 안내돼 사용자가 고칠 수 없는 말을 듣는다.
	private static final String EMAIL_CONSTRAINT = "uk_users_active_email";
	private static final String HANDLE_CONSTRAINT = "uk_users_active_handle";

	private DuplicateUserKeys() {
	}

	public static ErrorCode errorCodeOf(DataIntegrityViolationException exception) {
		String message = describe(exception).toLowerCase(Locale.ROOT);
		if (message.contains(HANDLE_CONSTRAINT)) {
			return ErrorCode.HANDLE_ALREADY_EXISTS;
		}
		if (message.contains(EMAIL_CONSTRAINT)) {
			return ErrorCode.EMAIL_ALREADY_EXISTS;
		}
		// 제약 이름을 못 읽는 드라이버·버전도 있다. 둘 중 하나이므로 흔한 쪽으로 답한다.
		return ErrorCode.EMAIL_ALREADY_EXISTS;
	}

	/** 제약 이름은 보통 가장 안쪽 원인 메시지에 들어 있다. */
	private static String describe(Throwable exception) {
		StringBuilder messages = new StringBuilder();
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			messages.append(cause.getMessage()).append('\n');
			if (cause.getCause() == cause) {
				break;
			}
		}
		return messages.toString();
	}
}
