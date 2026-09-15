package com.irene.twelvebooks.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 재설정 요청. 이메일 하나뿐이다.
 *
 * <p>형식 검증은 가입과 같은 규칙을 쓴다 — 보낼 수조차 없는 형태를 여기서 거른다. 다만
 * <b>그 주소에 계정이 있는지는 답하지 않는다.</b> 응답은 언제나 204다.
 */
public record PasswordResetRequest(

		@NotBlank
		@Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}$",
				message = "이메일 형식이 올바르지 않습니다")
		@Size(max = 255)
		String email) {
}
