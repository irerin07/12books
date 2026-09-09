package com.irene.twelvebooks.reading.dto;

/**
 * 방금 설정한 연간 목표와 그 해 완독 수.
 *
 * <p>목표를 세우지 않은 해를 기본 12권으로 답하는 것은 <b>조회</b>의 몫이고, 그 조회는
 * Phase 8의 프로필 통계에서 생긴다. 여기는 설정 결과만 돌려준다.
 */
public record GoalResponse(int year, int targetCount, long finishedCount) {
}
