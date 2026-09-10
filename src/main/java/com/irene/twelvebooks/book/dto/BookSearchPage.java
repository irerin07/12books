package com.irene.twelvebooks.book.dto;

import java.util.List;

/**
 * 검색 결과 한 페이지.
 *
 * <p>맨 배열로 내려주면 클라이언트가 다음 페이지가 있는지 알 수 없다. 결과가 비었을 때
 * "끝까지 봤다"와 "원래 없다"도 구분되지 않는다.
 *
 * <p>커서가 아니라 <b>쪽번호</b>인 것은 원본이 그렇기 때문이다. 카카오 검색은 page로 넘기고,
 * 우리가 커서를 지어내면 없는 순서를 있는 척하게 된다. 내부 DB 목록의 {@code CursorPage}와
 * 항목 이름({@code items}, {@code hasNext})만 맞춰 클라이언트가 같은 모양으로 다루게 했다.
 *
 * @param totalCount 카카오가 알려준 전체 결과 수. 모르면 0이다.
 */
public record BookSearchPage(List<BookSearchResult> items, int page, boolean hasNext, long totalCount) {

	public BookSearchPage withItems(List<BookSearchResult> replaced) {
		return new BookSearchPage(replaced, page, hasNext, totalCount);
	}
}
