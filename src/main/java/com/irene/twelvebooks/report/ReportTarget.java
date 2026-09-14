package com.irene.twelvebooks.report;

/**
 * 무엇을 신고했는가.
 *
 * <p>대상 테이블이 셋이라 외래 키를 걸 수 없다. 그래서 종류를 함께 저장한다 — 이것이 없으면
 * {@code target_id}가 어느 테이블의 id인지 알 수 없다.
 */
public enum ReportTarget {

	POST,
	COMMENT,
	USER
}
