package com.irene.twelvebooks.support;

import com.irene.twelvebooks.book.BookSignature;
import com.irene.twelvebooks.book.dto.BookRegisterRequest;
import com.irene.twelvebooks.book.dto.BookSearchResult;

import java.time.LocalDate;

/**
 * 등록 요청은 검색이 준 서명을 그대로 담아야 한다. 테스트에서 그 왕복을 짧게 쓰기 위한 도구.
 */
public final class SignedBookRequests {

	private SignedBookRequests() {
	}

	/** 본문 JSON을 손으로 쓸 때 필요한 서명 값만. */
	public static String signatureOf(BookSignature bookSignature, String isbn13, String title,
			String authors, String publisher, String thumbnailUrl, LocalDate publishedAt) {
		return bookSignature.signed(new BookSearchResult(
				isbn13, title, authors, publisher, thumbnailUrl, publishedAt, null)).signature();
	}

	public static BookRegisterRequest signed(BookSignature bookSignature, String isbn13, String title,
			String authors, String publisher, String thumbnailUrl, LocalDate publishedAt) {
		BookSearchResult result = bookSignature.signed(new BookSearchResult(
				isbn13, title, authors, publisher, thumbnailUrl, publishedAt, null));
		return new BookRegisterRequest(isbn13, title, authors, publisher, thumbnailUrl, publishedAt,
				result.signature());
	}
}
