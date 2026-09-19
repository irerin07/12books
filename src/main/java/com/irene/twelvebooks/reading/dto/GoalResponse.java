package com.irene.twelvebooks.reading.dto;

/**
 * 방금 설정한 연간 목표와 그 해 완독 수.
 *
 * <p>완독을 <b>두 숫자로</b> 준다. {@code finishedCount}는 책 종수이고 달성률의 분자다
 * — "올해 몇 권 읽었나"가 질문이라 같은 책을 두 번 읽어도 한 권이다.
 * {@code finishedSessionCount}는 회차 수라 두 번 읽으면 둘이다. 한 숫자로 뭉개면 둘 다
 * 잃는다 — 책 수만 주면 재독이 없던 일이 되고, 회차 수만 주면 얇은 책을 반복해 달성률을
 * 올릴 수 있다.
 *
 * <p>목표를 세우지 않은 해를 기본 12권으로 답하는 것은 <b>조회</b>의 몫이고, 그 조회는
 * Phase 8의 프로필 통계에서 생긴다. 여기는 설정 결과만 돌려준다.
 */
public record GoalResponse(int year, int targetCount, long finishedCount, long finishedSessionCount) {
}
