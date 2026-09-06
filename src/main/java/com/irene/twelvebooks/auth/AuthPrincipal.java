package com.irene.twelvebooks.auth;

/**
 * 검증된 access 토큰이 가리키는 사용자. 컨트롤러는 {@code @AuthUser}로 userId만 받는다.
 */
public record AuthPrincipal(Long userId, String handle) {
}
