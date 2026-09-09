package com.irene.twelvebooks.reading.dto;

/**
 * 연간 목표와 달성 현황. 목표를 세우지 않은 해도 기본 12권으로 답하므로 {@code targetCount}는
 * 항상 있다 — 클라이언트가 "목표 없음"을 따로 처리하지 않아도 된다.
 */
public record GoalResponse(int year, int targetCount, long finishedCount) {
}
