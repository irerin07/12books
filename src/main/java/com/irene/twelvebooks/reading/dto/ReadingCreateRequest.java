package com.irene.twelvebooks.reading.dto;

import com.irene.twelvebooks.reading.ReadingStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 책을 서재에 담는다. {@code bookId}는 {@code POST /books}로 확정한 내부 id다.
 * {@code status}를 보내지 않으면 "읽고 싶다"로 담긴다 — 사두고 아직 안 편 책이 기본이다.
 */
public record ReadingCreateRequest(@NotNull Long bookId, ReadingStatus status) {

	public ReadingStatus statusOrDefault() {
		return status == null ? ReadingStatus.WANT_TO_READ : status;
	}
}
