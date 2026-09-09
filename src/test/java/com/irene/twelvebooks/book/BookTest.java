package com.irene.twelvebooks.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 생성 시점에 지켜야 하는 불변식. 컬럼 제약(not null, isbn13/sourceKey 유일성)을 DB까지
 * 내려가서야 알게 되면 원인을 짚기 어렵다.
 */
class BookTest {

	private static final String ISBN = "9788960777330";

	@Test
	@DisplayName("제목이 없으면 만들 수 없다")
	void requiresTitle() {
		assertThatThrownBy(() -> Book.withIsbn13(ISBN, null, "저자", null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Book.withIsbn13(ISBN, "  ", "저자", null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("저자가 없으면 만들 수 없다")
	void requiresAuthors() {
		assertThatThrownBy(() -> Book.withIsbn13(ISBN, "제목", null, null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("키 없이는 만들 수 없다 — 어느 쪽으로도 유일성이 지켜지지 않는다")
	void requiresAKey() {
		assertThatThrownBy(() -> Book.withIsbn13(null, "제목", "저자", null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Book.withSourceKey(null, "제목", "저자", null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("두 팩토리는 각자의 키만 채운다")
	void fillsExactlyOneKey() {
		Book byIsbn = Book.withIsbn13(ISBN, "제목", "저자", null, null, null);
		assertThat(byIsbn.getIsbn13()).isEqualTo(ISBN);
		assertThat(byIsbn.getSourceKey()).isNull();

		Book bySourceKey = Book.withSourceKey("abc", "제목", "저자", null, null, null);
		assertThat(bySourceKey.getSourceKey()).isEqualTo("abc");
		assertThat(bySourceKey.getIsbn13()).isNull();
	}
}
