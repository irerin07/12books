package com.irene.twelvebooks.report;

/**
 * 신고의 처리 상태.
 *
 * <p>{@link #ACTIONED}와 {@link #REJECTED}를 나누는 이유는 기각도 <b>처리</b>이기 때문이다.
 * 하나로 합치면 "아직 안 본 신고"와 "보고 문제없다고 판단한 신고"가 같아져, 운영자가 같은
 * 신고를 계속 다시 본다.
 */
public enum ReportStatus {

	/** 아직 아무도 보지 않았다. */
	PENDING,

	/** 조치했다 — 글·댓글이면 숨겼다. */
	ACTIONED,

	/** 보고 문제없다고 판단했다. 숨겼던 것이 있으면 되돌린다. */
	REJECTED
}
