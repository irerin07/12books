package com.irene.twelvebooks.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Builder가 지켜야 하는 불변식. 컬럼 제약(not null, isbn13/sourceKey unique)을 DB까지
 * 내려가서야 알게 되면 원인을 짚기 어렵다.
 */
class BookTest {

	@Test
	@DisplayName("제목이 없으면 만들 수 없다")
	void requiresTitle() {
		assertThatThrownBy(() -> Book.builder().isbn13("9788960777330").authors("저자").build())
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("저자가 없으면 만들 수 없다")
	void requiresAuthors() {
		assertThatThrownBy(() -> Book.builder().isbn13("9788960777330").title("제목").build())
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("isbn13과 sourceKey는 정확히 하나여야 한다")
	void requiresExactlyOneKey() {
		assertThatThrownBy(() -> Book.builder().title("제목").authors("저자").build())
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Book.builder().isbn13("9788960777330").sourceKey("abc")
				.title("제목").authors("저자").build())
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("키가 하나만 있으면 만들어진다")
	void buildsWithOneKey() {
		assertThatCode(() -> Book.builder().isbn13("9788960777330").title("제목").authors("저자").build())
				.doesNotThrowAnyException();
		assertThatCode(() -> Book.builder().sourceKey("abc").title("제목").authors("저자").build())
				.doesNotThrowAnyException();
	}
}
