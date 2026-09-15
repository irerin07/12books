package com.irene.twelvebooks.auth.dto;

import com.irene.twelvebooks.common.validation.ByteSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 토큰과 새 비밀번호. 비밀번호 규칙은 가입과 같다 — 재설정으로 들어오면 더 약한 비밀번호를
 * 쓸 수 있다면 그 규칙은 규칙이 아니다.
 */
public record PasswordResetConfirmRequest(

		@NotBlank @Size(max = 200)
		String token,

		@NotBlank @Size(min = 8, max = 20)
		@ByteSize(max = 72, message = "비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다")
		String password) {
}
