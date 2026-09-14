package com.irene.twelvebooks.report;

/**
 * 왜 신고했는가. 운영자가 무엇부터 볼지 정하는 데 쓴다.
 *
 * <p>자유 입력만 받으면 같은 사유가 제각각 적혀 분류가 되지 않고, 너무 잘게 나누면 신고자가
 * 고르다 지친다. {@link #OTHER}와 {@code detail}이 나머지를 받는다.
 *
 * <p>{@link #SPOILER}는 이 서비스에만 있는 사유다 — 읽던 사람에게는 실질적인 피해이고,
 * 스포일러 표시를 하지 않은 글이 신고의 상당수가 될 것으로 본다.
 */
public enum ReportReason {

	SPAM,
	ABUSE,
	SEXUAL,
	SPOILER,
	OTHER
}
