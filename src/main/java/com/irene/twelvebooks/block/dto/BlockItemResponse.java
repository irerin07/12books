package com.irene.twelvebooks.block.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 내가 차단한 사람 한 줄. 관계의 id가 커서다.
 *
 * <p>팔로우 목록과 달리 {@code isFollowing} 같은 것을 싣지 않는다 — 차단한 사람에게 할 수
 * 있는 일은 차단을 푸는 것뿐이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlockItemResponse(Long id, String handle, String displayName, String avatarUrl) {
}
