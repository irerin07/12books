package com.irene.twelvebooks.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

	private static final String SECRET = "test-secret-key-for-unit-tests-0123456789";
	private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

	private final JwtProperties properties =
			new JwtProperties(SECRET, Duration.ofMinutes(10), Duration.ofDays(14));
	private final JwtProvider provider = new JwtProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	@DisplayName("발급한 access 토큰을 파싱하면 userId와 handle이 돌아온다")
	void issuesAndParsesAccessToken() {
		String token = provider.createAccessToken(42L, "irene");

		Optional<AuthPrincipal> principal = provider.parse(token);

		assertThat(principal).contains(new AuthPrincipal(42L, "irene"));
	}

	@Test
	@DisplayName("만료된 토큰은 거부한다")
	void rejectsExpiredToken() {
		String token = provider.createAccessToken(42L, "irene");

		// 발급 시점의 10분 뒤를 보는 provider로 같은 토큰을 읽는다
		JwtProvider later = new JwtProvider(properties,
				Clock.fixed(NOW.plus(Duration.ofMinutes(10)).plusSeconds(1), ZoneOffset.UTC));

		assertThat(later.parse(token)).isEmpty();
	}

	@Test
	@DisplayName("만료 직전의 토큰은 아직 유효하다")
	void acceptsTokenJustBeforeExpiry() {
		String token = provider.createAccessToken(42L, "irene");

		JwtProvider later = new JwtProvider(properties,
				Clock.fixed(NOW.plus(Duration.ofMinutes(10)).minusSeconds(1), ZoneOffset.UTC));

		assertThat(later.parse(token)).isPresent();
	}

	@Test
	@DisplayName("다른 키로 서명된 토큰은 거부한다")
	void rejectsTokenSignedWithAnotherKey() {
		String forged = Jwts.builder()
				.subject("42")
				.claim("handle", "irene")
				.expiration(Date.from(NOW.plus(Duration.ofMinutes(10))))
				.signWith(Keys.hmacShaKeyFor("another-secret-key-that-is-long-enough-0123".getBytes(StandardCharsets.UTF_8)))
				.compact();

		assertThat(provider.parse(forged)).isEmpty();
	}

	@Test
	@DisplayName("형식이 깨진 문자열은 예외 없이 거부한다")
	void rejectsMalformedToken() {
		assertThat(provider.parse("not-a-jwt")).isEmpty();
		assertThat(provider.parse("")).isEmpty();
		assertThat(provider.parse(null)).isEmpty();
	}

	@Test
	@DisplayName("secret이 HS256에 필요한 32바이트보다 짧으면 생성 시점에 막는다")
	void rejectsTooShortSecret() {
		JwtProperties weak = new JwtProperties("too-short", Duration.ofMinutes(10), Duration.ofDays(14));

		assertThatThrownBy(() -> new JwtProvider(weak, Clock.systemUTC()))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
