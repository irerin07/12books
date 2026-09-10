package com.irene.twelvebooks.user.dto;

import com.irene.twelvebooks.user.User;

/**
 * 공개 프로필. email과 passwordHash는 담지 않는다 — 자격증명은 어떤 응답에도 실리지 않는다.
 * 독서 통계는 해당 Phase에서 더한다.
 *
 * <p>팔로워·팔로잉 수는 <b>매번 세어</b> 넣는다. 반정규화 카운터는 갱신 유실과 불일치를 안고
 * 오므로, 이 조회가 실제로 느려진 뒤에 도입한다.
 */
public record ProfileResponse(
		String handle,
		String displayName,
		String bio,
		String avatarUrl,
		long followerCount,
		long followingCount) {

	public static ProfileResponse of(User user, long followerCount, long followingCount) {
		return new ProfileResponse(user.getHandle(), user.getDisplayName(), user.getBio(),
				user.getAvatarUrl(), followerCount, followingCount);
	}
}
