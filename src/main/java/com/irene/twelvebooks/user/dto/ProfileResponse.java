package com.irene.twelvebooks.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.user.User;

/**
 * 공개 프로필. email과 passwordHash는 담지 않는다 — 자격증명은 어떤 응답에도 실리지 않는다.
 * 독서 통계는 해당 Phase에서 더한다.
 *
 * <p>팔로워·팔로잉 수는 <b>매번 세어</b> 넣는다. 반정규화 카운터는 갱신 유실과 불일치를 안고
 * 오므로, 이 조회가 실제로 느려진 뒤에 도입한다.
 *
 * <p>비어 있는 값은 응답에서 뺀다. 같은 필드가 프로필에서는 {@code null}로 오고 목록에서는
 * 빠지면, 화면이 {@code null}과 {@code undefined}를 둘 다 다뤄야 한다 — 규칙이 아니라 사고다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
		String handle,
		String displayName,
		String bio,
		String avatarUrl,
		long followerCount,
		long followingCount,
		boolean isFollowing) {

	/**
	 * @param isFollowing <b>보는 사람이</b> 이 사람을 팔로우 중인지. 프로필마다 고정된 값이 아니라
	 *                    누가 보느냐에 따라 달라진다. 이 값이 없으면 화면이 팔로우 버튼을 처음
	 *                    그릴 때 어느 상태로 둘지 정할 수 없다.
	 */
	public static ProfileResponse of(User user, long followerCount, long followingCount,
			boolean isFollowing) {
		return new ProfileResponse(user.getHandle(), user.getDisplayName(), user.getBio(),
				user.getAvatarUrl(), followerCount, followingCount, isFollowing);
	}
}
