package com.irene.twelvebooks.reading.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.reading.Reading;
import com.irene.twelvebooks.reading.ReadingStatus;

import java.time.LocalDateTime;

/**
 * 아직 모르는 값은 응답에서 아예 뺀다 — null과 0을 클라이언트가 헷갈리지 않게.
 *
 * <p>{@code inBookshelf}는 항상 실린다. 서재 목록에서는 늘 참이지만, 책 화면에서 이 기록 하나만
 * 물어볼 때는 <b>지난 독서일 수도</b> 있어서 화면이 "담김"과 "전에 읽었음"을 구분해야 한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadingResponse(
		Long id,
		Long bookId,
		ReadingStatus status,
		int currentPage,
		Integer pageCount,
		LocalDateTime startedAt,
		LocalDateTime finishedAt,
		Integer rating,
		boolean inBookshelf) {

	public static ReadingResponse from(Reading reading) {
		return new ReadingResponse(reading.getId(), reading.getBookId(), reading.getStatus(),
				reading.getCurrentPage(), reading.getPageCount(), reading.getStartedAt(),
				reading.getFinishedAt(), reading.getRating(), reading.isInBookshelf());
	}
}
