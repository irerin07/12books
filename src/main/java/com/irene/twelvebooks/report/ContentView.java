package com.irene.twelvebooks.report;

/**
 * 운영자 목록에 곁들일 대상의 <b>id와 본문만</b>.
 *
 * <p>엔티티를 읽으면 쪽수·카운터·연결이 따라오는데 신고 한 줄에는 쓰이지 않는다. 알림이
 * 같은 이유로 {@code PostView}를 두고 있지만 그쪽은 <b>지운 글을 뺀다</b> — 여기는 반대로
 * 지운 것도 숨긴 것도 보여야 해서 같은 것을 쓸 수 없다.
 */
public record ContentView(Long id, String content) {
}
