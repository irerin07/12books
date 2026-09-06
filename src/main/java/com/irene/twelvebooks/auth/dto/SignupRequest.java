package com.irene.twelvebooks.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

		@NotBlank @Email @Size(max = 255)
		String email,

		@NotBlank @Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다")
		String password,

		@NotBlank @Pattern(regexp = "^[a-z0-9_]{3,20}$", message = "handle은 영소문자·숫자·_ 3~20자입니다")
		String handle,

		@NotBlank @Size(max = 50)
		String displayName) {
}
