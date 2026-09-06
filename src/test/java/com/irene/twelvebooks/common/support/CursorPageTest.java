package com.irene.twelvebooks.common.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CursorPageTest {

	@Test
	@DisplayName("size + 1건이 조회되면 마지막 1건을 버리고 hasNext를 세운다")
	void trimsExtraRowAndMarksHasNext() {
		List<Long> rows = List.of(30L, 29L, 28L);

		CursorPage<Long> page = CursorPage.of(rows, 2, id -> id);

		assertThat(page.items()).containsExactly(30L, 29L);
		assertThat(page.hasNext()).isTrue();
		assertThat(page.nextCursor()).isEqualTo(29L);
	}

	@Test
	@DisplayName("size 이하로 조회되면 마지막 페이지다")
	void lastPageHasNoCursor() {
		List<Long> rows = List.of(30L, 29L);

		CursorPage<Long> page = CursorPage.of(rows, 2, id -> id);

		assertThat(page.items()).containsExactly(30L, 29L);
		assertThat(page.hasNext()).isFalse();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	@DisplayName("결과가 없으면 빈 페이지다")
	void emptyPage() {
		CursorPage<Long> page = CursorPage.of(List.of(), 2, id -> id);

		assertThat(page.items()).isEmpty();
		assertThat(page.hasNext()).isFalse();
		assertThat(page.nextCursor()).isNull();
	}
}
