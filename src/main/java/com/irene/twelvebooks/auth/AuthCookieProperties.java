package com.irene.twelvebooks.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * refresh 쿠키의 브라우저 정책.
 *
 * <p>{@code SameSite}를 설정으로 뺀 이유가 있다. 기본값 {@code Strict}는 이 쿠키에 대한
 * CSRF 방어 그 자체다 — 외부 사이트발 요청에는 아예 실리지 않으므로, 인증을 헤더로만 받는
 * 이 API가 CSRF 토큰 없이도 안전하다(§{@link SecurityConfig}).
 *
 * <p>그런데 프런트가 <b>다른 사이트</b>에 뜨면 그 정책이 정상 요청까지 막는다. 로그인은
 * 되지만 재발급이 쿠키를 못 받아 access 만료마다 끊긴다. 그때만 {@code None}으로 낮춘다 —
 * 낮추는 순간 이 쿠키의 CSRF 방어가 사라지므로, <b>결정이 배포 설정에 드러나야</b> 한다.
 * 코드에 박아 두면 왜 약해졌는지 아무도 모른다.
 *
 * @param refreshCookieSameSite {@code Strict}(기본) · {@code Lax} · {@code None}.
 *                              {@code None}은 브라우저가 {@code Secure} 없이는 받지 않는데,
 *                              이 쿠키는 언제나 {@code Secure}라 그 조건은 늘 맞는다.
 */
@ConfigurationProperties(prefix = "twelvebooks.auth")
public record AuthCookieProperties(String refreshCookieSameSite) {

	public AuthCookieProperties {
		refreshCookieSameSite = (refreshCookieSameSite == null || refreshCookieSameSite.isBlank())
				? "Strict" : refreshCookieSameSite;
	}
}
