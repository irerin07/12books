package com.irene.twelvebooks.follow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.user.User;

/**
 * 팔로워·팔로잉 목록의 한 줄. 사람 정보에 <b>보는 사람과의 관계</b>가 붙는다.
 *
 * <p>{@code UserSummaryResponse}에 {@code isFollowing}을 더하지 않고 따로 둔 이유가 있다.
 * 그 타입은 {@code PostResponse.author}에도 박혀 있는데, 피드에서는 이 관계를 계산하지 않는다.
 * 거기까지 필드가 따라가면 <b>항상 거짓인 값</b>이 응답에 실려 거짓말이 된다. 계산하지 않는
 * 자리에는 애초에 그 필드가 없어야 한다.
 *
 * @param isFollowing 목록을 <b>보는 사람이</b> 이 줄의 사람을 팔로우 중인지. 목록 주인이 아니라
 *                    보는 사람 기준이다 — 남의 팔로워 목록을 볼 때도 버튼은 내 관계를 따른다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FollowItemResponse(String handle, String displayName, String avatarUrl,
		boolean isFollowing) {

	public static FollowItemResponse of(User user, boolean isFollowing) {
		return new FollowItemResponse(user.getHandle(), user.getDisplayName(), user.getAvatarUrl(),
				isFollowing);
	}
}
