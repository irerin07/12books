package com.irene.twelvebooks.reading.dto;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.dto.BookResponse;
import com.irene.twelvebooks.reading.Reading;

/**
 * 서재 한 칸. 표지 그리드가 주 용도라 책 정보가 항상 함께 나간다.
 *
 * <p>책은 페이지의 reading들을 모아 한 번에 조회한다 — 항목마다 따로 읽으면 페이지 크기만큼
 * 쿼리가 늘어난다.
 */
public record LibraryItemResponse(ReadingResponse reading, BookResponse book) {

	public static LibraryItemResponse of(Reading reading, Book book) {
		return new LibraryItemResponse(ReadingResponse.from(reading), BookResponse.from(book));
	}
}
