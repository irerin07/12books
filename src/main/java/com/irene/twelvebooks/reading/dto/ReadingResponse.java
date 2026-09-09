package com.irene.twelvebooks.reading.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.reading.Reading;
import com.irene.twelvebooks.reading.ReadingStatus;

import java.time.LocalDateTime;

/** 아직 모르는 값은 응답에서 아예 뺀다 — null과 0을 클라이언트가 헷갈리지 않게. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadingResponse(
		Long id,
		Long bookId,
		ReadingStatus status,
		int currentPage,
		Integer pageCount,
		LocalDateTime startedAt,
		LocalDateTime finishedAt,
		Integer rating) {

	public static ReadingResponse from(Reading reading) {
		return new ReadingResponse(reading.getId(), reading.getBookId(), reading.getStatus(),
				reading.getCurrentPage(), reading.getPageCount(), reading.getStartedAt(),
				reading.getFinishedAt(), reading.getRating());
	}
}
