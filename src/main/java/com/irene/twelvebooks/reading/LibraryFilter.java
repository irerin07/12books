package com.irene.twelvebooks.reading;

/**
 * 서재 조회 필터. 주어진 것끼리 AND로 묶이고, 주지 않은 것(null)은 조건에서 빠진다.
 *
 * @param year         시작 연도와 완독 연도 중 하나라도 이 해면 포함 — "그 해에 손댄 책"
 * @param startedYear  이 해에 읽기 시작한 책
 * @param finishedYear 이 해에 다 읽은 책
 */
public record LibraryFilter(ReadingStatus status, Integer year, Integer startedYear, Integer finishedYear) {
}
