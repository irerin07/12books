package com.irene.twelvebooks.reading.dto;

import com.irene.twelvebooks.reading.ReadingStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 책을 서재에 담는다. {@code bookId}는 {@code POST /books}로 확정한 내부 id다.
 * {@code status}를 보내지 않으면 "읽고 싶다"로 담긴다 — 사두고 아직 안 편 책이 기본이다.
 */
public record ReadingCreateRequest(@NotNull Long bookId, ReadingStatus status, Boolean resume) {

	/**
	 * 전에 읽던 기록이 있을 때 <b>이어서 읽을지</b>. 보내지 않으면 서버가 대신 고르지 않고
	 * {@code R003}으로 되돌려보낸다 — 어느 쪽을 골라도 사용자를 놀라게 하기 때문이다.
	 * 예전 진도를 되살리면 지운 줄 알았던 것이 돌아오고, 0쪽부터 시작하면 읽은 기록이
	 * 사라진 것처럼 보인다.
	 *
	 * <p>지난 기록이 없으면 이 값은 쓰이지 않는다.
	 */
	public Boolean resume() {
		return resume;
	}

	public ReadingStatus statusOrDefault() {
		return status == null ? ReadingStatus.WANT_TO_READ : status;
	}
}
