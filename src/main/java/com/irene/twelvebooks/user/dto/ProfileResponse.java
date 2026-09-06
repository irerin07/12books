package com.irene.twelvebooks.user.dto;

import com.irene.twelvebooks.user.User;

/**
 * 공개 프로필. email과 passwordHash는 담지 않는다 — 자격증명은 어떤 응답에도 실리지 않는다.
 * 팔로워·독서 통계는 해당 Phase에서 더한다.
 */
public record ProfileResponse(String handle, String displayName, String bio, String avatarUrl) {

	public static ProfileResponse from(User user) {
		return new ProfileResponse(user.getHandle(), user.getDisplayName(), user.getBio(), user.getAvatarUrl());
	}
}
