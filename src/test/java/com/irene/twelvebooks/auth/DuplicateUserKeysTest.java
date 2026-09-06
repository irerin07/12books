package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사전 조회를 함께 통과한 동시 가입은 유니크 제약에서 갈린다.
 * 그때 어느 제약이 터졌는지에 따라 사용자에게 다른 안내가 나가야 한다.
 */
class DuplicateUserKeysTest {

	@Test
	@DisplayName("handle 제약이 터지면 handle 중복으로 답한다")
	void mapsHandleConstraint() {
		var exception = new DataIntegrityViolationException(
				"Duplicate entry 'irene' for key 'users.uk_users_handle'");

		assertThat(DuplicateUserKeys.errorCodeOf(exception)).isEqualTo(ErrorCode.HANDLE_ALREADY_EXISTS);
	}

	@Test
	@DisplayName("email 제약이 터지면 email 중복으로 답한다")
	void mapsEmailConstraint() {
		var exception = new DataIntegrityViolationException(
				"Duplicate entry 'irene@example.com' for key 'users.uk_users_email'");

		assertThat(DuplicateUserKeys.errorCodeOf(exception)).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
	}

	@Test
	@DisplayName("제약 이름을 알 수 없으면 email 중복으로 답한다")
	void fallsBackToEmail() {
		var exception = new DataIntegrityViolationException("알 수 없는 제약");

		assertThat(DuplicateUserKeys.errorCodeOf(exception)).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
	}
}
