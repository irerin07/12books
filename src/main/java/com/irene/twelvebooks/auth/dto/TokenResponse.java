package com.irene.twelvebooks.auth.dto;

/**
 * refresh는 여기 담지 않는다. HttpOnly 쿠키로만 나가므로 JS가 읽을 수 없어야 한다.
 */
public record TokenResponse(String accessToken) {
}
