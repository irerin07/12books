package com.irene.twelvebooks.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalTest {

	@Test
	@DisplayName("탭·개행도 공백 하나로 모은다")
	void collapsesAllWhitespace() {
		assertThat(Canonical.normalized("코드\t컴플리트")).isEqualTo("코드 컴플리트");
		assertThat(Canonical.normalized("코드\n컴플리트")).isEqualTo("코드 컴플리트");
		assertThat(Canonical.normalized("  코드 \u00a0 컴플리트  ")).isEqualTo("코드 컴플리트");
	}

	@Test
	@DisplayName("필드 경계는 길이로 복원된다 — 이어붙인 결과가 같아질 수 없다")
	void keepsFieldBoundaries() {
		assertThat(Canonical.join("제목|저자", "출판사")).isNotEqualTo(Canonical.join("제목", "저자|출판사"));
	}

	@Test
	@DisplayName("null과 빈 문자열은 다르다")
	void separatesNullFromEmpty() {
		assertThat(Canonical.join((String) null)).isNotEqualTo(Canonical.join(""));
	}
}
