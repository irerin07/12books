package com.irene.twelvebooks.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookSignatureTest {

	@Test
	@DisplayName("서명 키가 짧으면 기동하지 않는다 — 메타데이터 오염을 막는 유일한 방어선이다")
	void rejectsShortSecret() {
		assertThatThrownBy(() -> new BookSignature(new BookProperties("a")))
				.isInstanceOf(IllegalStateException.class);
		// 31바이트: 경계 바로 아래
		assertThatThrownBy(() -> new BookSignature(new BookProperties("a".repeat(31))))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("32바이트부터 기동한다 — 길이는 UTF-8 바이트로 센다")
	void acceptsLongEnoughSecret() {
		assertThatCode(() -> new BookSignature(new BookProperties("a".repeat(32))))
				.doesNotThrowAnyException();
		// 한글 11자 = 33바이트. 문자 수로 세면 11로 보여 통과하지 못한다.
		assertThatCode(() -> new BookSignature(new BookProperties("가".repeat(11))))
				.doesNotThrowAnyException();
	}
}
