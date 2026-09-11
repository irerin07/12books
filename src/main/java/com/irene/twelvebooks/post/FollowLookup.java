package com.irene.twelvebooks.post;

import java.util.List;
import java.util.Set;

/**
 * "이 사람들 중 내가 팔로우 중인 사람은 누구인가"를 묻는 창구.
 *
 * <p>{@code post}가 {@code follow} 패키지를 알지 않으면서도 관계를 물을 수 있게 하는 자리다.
 * 구현은 {@code feed}가 넘겨준다 — 조합은 거기서 한다는 원칙 그대로다.
 *
 * <p>인자가 <b>그 페이지에 실린 작성자들</b>인 것이 핵심이다. 팔로잉 전체를 미리 끌어와
 * Set으로 만들면 한 페이지가 50건이어도 비용이 팔로잉 수에 비례하고, 페이지가 비어 있을 때조차
 * 같은 값을 치른다.
 */
@FunctionalInterface
public interface FollowLookup {

	Set<Long> followedAmong(List<Long> authorIds);
}
