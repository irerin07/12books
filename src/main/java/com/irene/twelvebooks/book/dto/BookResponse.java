package com.irene.twelvebooks.book.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irene.twelvebooks.book.Book;

import java.time.LocalDate;

/**
 * 아직 모르는 값은 응답에서 아예 뺀다 — null과 "0"을 클라이언트가 헷갈리지 않게.
 *
 * <p>총 쪽수는 여기 없다. 판본마다 다르고 사용자가 채우는 값이라 {@code readings}가 갖는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookResponse(
		Long id,
		String isbn13,
		String title,
		String authors,
		String publisher,
		String thumbnailUrl,
		LocalDate publishedAt) {

	public static BookResponse from(Book book) {
		return new BookResponse(book.getId(), book.getIsbn13(), book.getTitle(), book.getAuthors(),
				book.getPublisher(), book.getThumbnailUrl(), book.getPublishedAt());
	}
}
