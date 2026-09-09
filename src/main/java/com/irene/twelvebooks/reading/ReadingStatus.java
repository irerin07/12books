package com.irene.twelvebooks.reading;

/**
 * 서재에 담은 책의 상태. 어떤 상태에서 어떤 상태로든 갈 수 있다 — 읽다 말고 덮었다가
 * 몇 달 뒤 다시 펴는 것이 정상이라, 전이를 막는 규칙은 두지 않는다.
 * 전이에 따라오는 부수 효과(시작일·완독일)만 {@link Reading}이 관리한다.
 */
public enum ReadingStatus {

	WANT_TO_READ,
	READING,
	FINISHED,
	PAUSED,
	DROPPED
}
