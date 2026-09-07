package com.irene.twelvebooks.book.dto;

import java.time.LocalDate;

/**
 * 카카오 검색 결과 한 건. 저장되지 않고 그대로 응답으로 나간다.
 */
public record BookSearchResult(
		String isbn13,
		String title,
		String authors,
		String publisher,
		String thumbnailUrl,
		LocalDate publishedAt) {
}
