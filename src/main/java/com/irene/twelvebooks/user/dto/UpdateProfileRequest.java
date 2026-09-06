package com.irene.twelvebooks.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 부분 수정. 필드를 보내지 않으면 null이고, 그 값은 바꾸지 않는다.
 * handle·email이 없는 것은 의도다 — 몰래 실어 보내도 무시되는 게 아니라 애초에 받지 않는다.
 */
public record UpdateProfileRequest(

		@Size(min = 1, max = 50)
		String displayName,

		@Size(max = 200)
		String bio,

		@Size(max = 500)
		String avatarUrl) {
}
