package com.irene.twelvebooks.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.user.User;

/**
 * 목록에 함께 실리는 최소한의 사람 정보. 감상평 한 줄에 이름과 얼굴이 없으면 누가 쓴 글인지
 * 보려고 다시 요청해야 한다.
 *
 * <p>{@link ProfileResponse}와 나누는 이유는 담기는 자리가 다르기 때문이다. 프로필은 한 사람을
 * 보여주는 화면이라 소개글까지 들어가지만, 여기는 스무 건이 함께 실리는 자리라 가벼워야 한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserSummaryResponse(String handle, String displayName, String avatarUrl) {

	public static UserSummaryResponse from(User user) {
		return new UserSummaryResponse(user.getHandle(), user.getDisplayName(), user.getAvatarUrl());
	}
}
