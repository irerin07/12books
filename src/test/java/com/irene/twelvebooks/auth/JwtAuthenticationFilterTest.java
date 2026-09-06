package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(JwtAuthenticationFilterTest.ProtectedEndpoint.class)
class JwtAuthenticationFilterTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	JwtProperties jwtProperties;

	@Test
	@DisplayName("토큰 없이 보호된 경로를 부르면 401이고 본문은 공통 에러 형식이다")
	void rejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(get("/test/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorCodeAssertions.UNAUTHORIZED_CODE));
	}

	@Test
	@DisplayName("유효한 access 토큰이면 통과하고 @AuthUser로 userId가 주입된다")
	void resolvesUserIdFromValidToken() throws Exception {
		String token = jwtProvider.createAccessToken(42L, "irene");

		mockMvc.perform(get("/test/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().string("42"));
	}

	@Test
	@DisplayName("다른 키로 서명된 토큰은 401")
	void rejectsForgedToken() throws Exception {
		JwtProvider other = new JwtProvider(
				new JwtProperties("another-secret-key-that-is-long-enough-0123", Duration.ofMinutes(10), Duration.ofDays(14)),
				Clock.systemUTC());

		mockMvc.perform(get("/test/me").header("Authorization", "Bearer " + other.createAccessToken(42L, "irene")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("만료된 토큰은 401 — 필터가 예외를 던지지 않는다")
	void rejectsExpiredToken() throws Exception {
		JwtProvider past = new JwtProvider(jwtProperties,
				Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC));

		mockMvc.perform(get("/test/me").header("Authorization", "Bearer " + past.createAccessToken(42L, "irene")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Bearer 접두사가 없거나 값이 비면 401")
	void rejectsMalformedAuthorizationHeader() throws Exception {
		String token = jwtProvider.createAccessToken(42L, "irene");

		mockMvc.perform(get("/test/me").header("Authorization", token))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/test/me").header("Authorization", "Bearer "))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("공개 경로는 토큰 없이도 열려 있다")
	void allowsPublicPaths() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@RestController
	static class ProtectedEndpoint {

		@GetMapping("/test/me")
		String me(@AuthUser Long userId) {
			return String.valueOf(userId);
		}
	}

	static final class ErrorCodeAssertions {
		static final String UNAUTHORIZED_CODE = "A001";
	}
}
