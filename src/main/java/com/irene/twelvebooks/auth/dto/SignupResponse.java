package com.irene.twelvebooks.auth.dto;

import com.irene.twelvebooks.user.User;

public record SignupResponse(Long id, String handle, String displayName) {

	public static SignupResponse from(User user) {
		return new SignupResponse(user.getId(), user.getHandle(), user.getDisplayName());
	}
}
