package com.irene.twelvebooks.notification;

/**
 * 알림이 가리키는 글. 화면은 id와 본문만 쓴다.
 *
 * <p>지운 글은 애초에 조회에서 빠지므로 여기 담기지 않는다.
 */
public record PostView(Long id, String content) {
}
