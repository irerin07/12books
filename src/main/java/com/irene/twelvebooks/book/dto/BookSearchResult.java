package com.irene.twelvebooks.book.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

/**
 * 카카오 검색 결과 한 건. 저장되지 않고 그대로 응답으로 나간다.
 *
 * <p>{@code signature}는 서버가 붙인다. 등록할 때 이 값을 그대로 되돌려보내야 한다 —
 * 검색을 거치지 않은 임의의 메타데이터가 공용 books 테이블에 들어오는 것을 막는다.
 *
 * <p>비어 있는 값은 응답에서 뺀다. 이 객체는 <b>그대로 되돌려보내 등록하는</b> 페이로드이기도
 * 한데, 빠진 것과 {@code null}인 것은 서버에서 같은 값이라 서명이 그대로 맞는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
