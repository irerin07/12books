package com.irene.twelvebooks.book;

/**
 * 검색 범위. 제목과 저자를 한꺼번에 찾으면 원하는 책을 고르기 어렵다.
 *
 * <p>실측(2026-09-11, {@code external-apis.md}): {@code 박경리}는 전체 628건인데 저자로 좁히면
 * 568건, 제목으로 좁히면 228건이다. 섞여 있으면 "박경리가 쓴 책"도 "제목에 박경리가 든 책"도
 * 둘 다 묻힌다.
 *
 * <p>카카오의 값을 그대로 노출하지 않고 우리 이름을 쓴다. 저자는 카카오에서 {@code person}
 * (인명)인데, 독서 기록 앱에서 그 단어는 뜻이 불분명하다. 상류의 어휘가 우리 API에 새면
 * 나중에 검색 제공자를 바꿀 때 클라이언트까지 따라 바뀐다.
 */
public enum BookSearchTarget {

	/** 좁히지 않는다. 카카오에 target을 아예 보내지 않는 것이 전체 검색이다. */
	ALL(null),

	TITLE("title"),

	/** 카카오의 {@code person}(인명). 저자·역자 등이 걸린다. */
	AUTHOR("person");

	private final String kakaoTarget;

	BookSearchTarget(String kakaoTarget) {
		this.kakaoTarget = kakaoTarget;
	}

	/** {@code null}이면 좁히지 않는다는 뜻이다. */
	public String kakaoTarget() {
		return kakaoTarget;
	}
}
