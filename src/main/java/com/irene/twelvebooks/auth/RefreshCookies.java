package com.irene.twelvebooks.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh 토큰을 담는 쿠키를 만든다.
 *
 * <p>{@code Path}를 인증 엔드포인트로 좁혀 다른 요청에는 아예 실려 나가지 않게 하고,
 * {@code SameSite=Strict}로 외부 사이트발 요청에 쿠키가 붙지 않게 한다 — 이것이
 * reissue·logout에 대한 CSRF 방어다.
 */
@Component
public class RefreshCookies {

	public static final String NAME = "refreshToken";
	private static final String PATH = "/api/v1/auth";

	private final Duration refreshTokenTtl;

	public RefreshCookies(JwtProperties properties) {
		this.refreshTokenTtl = properties.refreshTokenTtl();
	}

	public void set(HttpHeaders headers, String refreshToken) {
		headers.add(HttpHeaders.SET_COOKIE, build(refreshToken, refreshTokenTtl).toString());
	}

	public void clear(HttpHeaders headers) {
		headers.add(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
	}

	private ResponseCookie build(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path(PATH)
				.maxAge(maxAge)
				.build();
	}
}
