package com.irene.twelvebooks.auth;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Access 토큰 발급·검증. subject에 userId, claim에 handle을 담는다.
 * Refresh는 JWT가 아니라 Redis가 진실 공급원인 불투명 문자열이므로 여기서 다루지 않는다.
 */
@Component
public class JwtProvider {

	/** HS256이 요구하는 키 길이. */
	private static final int MINIMUM_SECRET_BYTES = 32;

	private static final String HANDLE_CLAIM = "handle";

	private final SecretKey key;
	private final JwtParser parser;
	private final Duration accessTokenTtl;
	private final Clock clock;

	public JwtProvider(JwtProperties properties, Clock clock) {
		byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < MINIMUM_SECRET_BYTES) {
			// 짧은 키는 서명을 그만큼 쉽게 위조하게 만든다. 첫 요청이 아니라 기동에서 막는다.
			throw new IllegalArgumentException(
					"JWT secret은 최소 %d바이트여야 합니다. 현재 %d바이트".formatted(MINIMUM_SECRET_BYTES, secret.length));
		}
		this.key = Keys.hmacShaKeyFor(secret);
		// 파서는 상태가 없고 스레드 안전하다. 매 요청마다 다시 만들 이유가 없다.
		this.parser = Jwts.parser()
				.verifyWith(this.key)
				.clock(() -> Date.from(clock.instant()))
				.build();
		this.accessTokenTtl = properties.accessTokenTtl();
		this.clock = clock;
	}

	public String createAccessToken(Long userId, String handle) {
		Instant now = clock.instant();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim(HANDLE_CLAIM, handle)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(accessTokenTtl)))
				.signWith(key)
				.compact();
	}

	/**
	 * 검증에 실패하면 예외 대신 빈 값을 돌려준다. 필터가 익명으로 통과시키고
	 * {@code AuthenticationEntryPoint}가 401을 만들게 하기 위해서다.
	 */
	public Optional<AuthPrincipal> parse(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		try {
			var claims = parser.parseSignedClaims(token).getPayload();
			return Optional.of(new AuthPrincipal(
					Long.valueOf(claims.getSubject()), claims.get(HANDLE_CLAIM, String.class)));
		}
		catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
