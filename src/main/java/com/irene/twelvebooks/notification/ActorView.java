package com.irene.twelvebooks.notification;

/**
 * 알림 한 줄에 붙는 사람. 화면이 쓰는 값이 전부다.
 *
 * <p>엔티티를 통째로 읽지 않는 이유가 있다. {@code users}에는 {@code password_hash}가 있는데,
 * 알림 목록을 한 번 열 때마다 스무 명의 해시를 메모리로 끌어올릴 이유가 없다. 조회 전용이라는
 * 의도도 타입으로 드러난다.
 *
 * <p>이 프로젝트의 다른 목록(감상평·댓글)은 아직 엔티티를 읽는다. 여기만 바꾼 것은 리뷰가
 * 여기를 짚었기 때문이고 공통 조회 틀을 세우려는 것이 아니다 — 필요해지면 그때 옮긴다.
 */
public record ActorView(Long id, String handle, String displayName, String avatarUrl) {
}
