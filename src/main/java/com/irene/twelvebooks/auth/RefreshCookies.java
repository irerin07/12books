package com.irene.twelvebooks.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh 토큰을 담는 쿠키를 만든다.
 *
 * <p>{@code Path}를 인증 엔드포인트로 좁혀 다른 요청에는 아예 실려 나가지 않게 하고,
 * {@code SameSite}로 외부 사이트발 요청에 쿠키가 붙지 않게 한다 — 이것이 reissue·logout에
 * 대한 CSRF 방어다. 프런트가 다른 사이트에 뜨면 그 정책을 낮춰야 하는데, 낮추는 결정이
 * 배포 설정에 드러나도록 값을 밖에서 받는다({@link AuthCookieProperties}).
 */
@Component
public class RefreshCookies {

	public static final String NAME = "refreshToken";
	private static final String PATH = "/api/v1/auth";

	private final Duration refreshTokenTtl;
	private final String sameSite;

	public RefreshCookies(JwtProperties properties, AuthCookieProperties cookieProperties) {
		this.refreshTokenTtl = properties.refreshTokenTtl();
		this.sameSite = cookieProperties.refreshCookieSameSite();
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
				.sameSite(sameSite)
				.path(PATH)
				.maxAge(maxAge)
				.build();
	}
}
