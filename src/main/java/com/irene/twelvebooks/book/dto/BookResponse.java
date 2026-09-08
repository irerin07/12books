package com.irene.twelvebooks.book.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.book.Book;

import java.time.LocalDate;

/**
 * 아직 모르는 값(총 쪽수 등)은 응답에서 아예 뺀다 — null과 "0쪽"을 클라이언트가 헷갈리지 않게.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookResponse(
		Long id,
		String isbn13,
		String title,
		String authors,
		String publisher,
		String thumbnailUrl,
		Integer pageCount,
		LocalDate publishedAt) {

	public static BookResponse from(Book book) {
		return new BookResponse(book.getId(), book.getIsbn13(), book.getTitle(), book.getAuthors(),
				book.getPublisher(), book.getThumbnailUrl(), book.getPageCount(), book.getPublishedAt());
	}
}
