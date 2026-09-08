package com.irene.twelvebooks.book.dto;

import java.time.LocalDate;

/**
 * 카카오 검색 결과 한 건. 저장되지 않고 그대로 응답으로 나간다.
 *
 * <p>{@code signature}는 서버가 붙인다. 등록할 때 이 값을 그대로 되돌려보내야 한다 —
 * 검색을 거치지 않은 임의의 메타데이터가 공용 books 테이블에 들어오는 것을 막는다.
 */
public record BookSearchResult(
		String isbn13,
		String title,
		String authors,
		String publisher,
		String thumbnailUrl,
		LocalDate publishedAt,
		String signature) {

	public BookSearchResult withSignature(String signature) {
		return new BookSearchResult(isbn13, title, authors, publisher, thumbnailUrl, publishedAt, signature);
	}
}
