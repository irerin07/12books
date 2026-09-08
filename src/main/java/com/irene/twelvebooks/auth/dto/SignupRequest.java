package com.irene.twelvebooks.auth.dto;

import com.irene.twelvebooks.common.validation.ByteSize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

		// @Email 기본 검증은 "@와 점이 있으면" 통과라 한글이 섞인 주소나 TLD 없는
		// irene@localhost도 받아들인다. 실재하는 주소인지는 확인 메일만이 알 수 있지만,
		// 최소한 보낼 수조차 없는 형태는 여기서 거른다.
		@NotBlank
		@Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}$",
				message = "이메일 형식이 올바르지 않습니다")
		@Size(max = 255)
		String email,

		// BCrypt는 UTF-8 72바이트를 넘기면 예외를 던진다. 글자 수만 세면 한글 25자가
		// 검증을 통과한 뒤 인코딩에서 터진다(75바이트).
		// ByteSize는 20자 상한이 생기면서 실제로는 걸릴 일이 없다(20자는 아무리 길어야 60바이트).
		// 상한을 나중에 올릴 때 BCrypt 제한이 조용히 사라지지 않도록 남겨둔다.
		@NotBlank @Size(min = 8, max = 20) @ByteSize(max = 72, message = "비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다")
		String password,

		@NotBlank @Pattern(regexp = "^[a-z0-9_]{3,20}$", message = "handle은 영소문자·숫자·_ 3~20자입니다")
		String handle,

		@NotBlank @Size(max = 50)
		String displayName) {
}
