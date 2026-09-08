package com.irene.twelvebooks.book;

import java.text.Normalizer;

/**
 * 여러 필드를 하나의 문자열로 합칠 때 쓰는 직렬화.
 *
 * <p>단순히 구분자로 이으면 필드 경계가 사라진다 — {@code ("제목|저자", "출판사")}와
 * {@code ("제목", "저자|출판사")}가 똑같은 {@code "제목|저자|출판사"}가 된다. 해시 충돌이
 * 아니라 직렬화 문제이고, 서로 다른 책이 같은 sourceKey로 합쳐지거나 서명이 다른 값에
 * 재사용되는 결과로 이어진다.
 *
 * <p>각 조각 앞에 길이를 붙여 경계를 복원 가능하게 만든다. null은 빈 문자열과 구분한다.
 */
final class Canonical {

	private static final String NULL_MARK = "-1:|";

	private Canonical() {
	}

	static String join(String... parts) {
		StringBuilder joined = new StringBuilder();
		for (String part : parts) {
			if (part == null) {
				joined.append(NULL_MARK);
			}
			else {
				joined.append(part.length()).append(':').append(part).append('|');
			}
		}
		return joined.toString();
	}

	/**
	 * 같은 책을 묶기 위한 정규화. 앞뒤 공백과 겹공백, 그리고 자모가 분리된 한글처럼
	 * 눈에는 같지만 코드포인트가 다른 표기를 하나로 모은다.
	 *
	 * <p>정규식을 쓰지 않는다. 자바 문자열에 백슬래시를 한 번만 적으면 Java 15부터
	 * {@code \s}가 <b>공백 문자 하나</b>를 뜻하는 문자열 이스케이프로 먼저 해석되어,
	 * 컴파일은 되지만 패턴이 " +"가 되고 탭과 개행은 그대로 남는다. 조용히 반만 동작하는
	 * 종류의 실수라, 여기서는 문자를 직접 훑는다.
	 */
	static String normalized(String value) {
		if (value == null) {
			return null;
		}
		StringBuilder collapsed = new StringBuilder(value.length());
		boolean pendingSpace = false;
		for (int i = 0; i < value.length(); i++) {
			char each = value.charAt(i);
			if (isSpace(each)) {
				// 앞이 비어 있으면 여는 공백이라 버린다. 닫는 공백은 끝내 붙이지 않는다.
				pendingSpace = !collapsed.isEmpty();
				continue;
			}
			if (pendingSpace) {
				collapsed.append(' ');
				pendingSpace = false;
			}
			collapsed.append(each);
		}
		return Normalizer.normalize(collapsed, Normalizer.Form.NFC);
	}

	/** 탭·개행에 더해 줄바꿈 없는 공백(U+00A0)처럼 눈에 보이지 않는 공백까지 공백으로 본다. */
	private static boolean isSpace(char each) {
		return Character.isWhitespace(each) || Character.getType(each) == Character.SPACE_SEPARATOR;
	}
}
