package com.irene.twelvebooks.common.support;

/**
 * 목록 한 페이지의 크기. 클라이언트가 10000을 보내도 한 번에 나가는 것은 50건이다.
 *
 * <p>상한을 컨트롤러에서 자르는 이유는 서비스가 "크기는 이미 안전하다"를 전제로 쓰도록 하기
 * 위해서다. 목록이 늘 때마다 같은 숫자를 다시 적으면 어딘가 한 곳만 다른 상한을 갖게 된다.
 */
public final class PageSize {

	public static final int DEFAULT = 20;
	public static final int MAX = 50;

	private PageSize() {
	}

	/** 0이나 음수는 잘못 보낸 값이라 거부 대신 기본값으로 되돌린다 — 목록 조회를 실패시킬 이유가 없다. */
	public static int clamp(int size) {
		if (size < 1) {
			return DEFAULT;
		}
		return Math.min(size, MAX);
	}
}
