package com.irene.twelvebooks.e2e;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1의 핵심 여정 하나를 끝까지 통과시킨다.
 * 가입 → 로그인 → 보호된 호출 → 재발급 → 로그아웃 → 재발급 거부.
 */
class AuthJourneyE2ETest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StringRedisTemplate redis;

	@BeforeEach
	void clean() {
		userRepository.deleteAll();
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	@Test
	@DisplayName("가입부터 로그아웃까지 한 번에 통과한다")
	void walksTheWholeJourney() throws Exception {
		// 1. 가입
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"irene@example.com","password":"password123","handle":"irene","displayName":"아이린"}"""))
				.andExpect(status().isCreated());

		// 2. 토큰 없이는 막힌다
		mockMvc.perform(patch("/api/v1/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bio":"몰래"}"""))
				.andExpect(status().isUnauthorized());

		// 3. 로그인 — access는 본문, refresh는 쿠키
		var loginResponse = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"irene@example.com","password":"password123"}"""))
				.andExpect(status().isOk())
				.andReturn().getResponse();

		String accessToken = com.jayway.jsonpath.JsonPath.read(loginResponse.getContentAsString(), "$.accessToken");
		Cookie refresh = loginResponse.getCookie("refreshToken");

		// 4. access로 내 프로필을 고친다
		mockMvc.perform(patch("/api/v1/me")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bio":"열두 권을 읽는 중"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bio").value("열두 권을 읽는 중"));

		// 5. 공개 프로필에도 반영된다
		mockMvc.perform(get("/api/v1/users/irene").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bio").value("열두 권을 읽는 중"))
				.andExpect(jsonPath("$.email").doesNotExist());

		// 6. refresh 쿠키로 재발급 — 새 access와 새 쿠키
		var reissued = mockMvc.perform(post("/api/v1/auth/reissue").cookie(refresh))
				.andExpect(status().isOk())
				.andReturn().getResponse();

		String newAccessToken = com.jayway.jsonpath.JsonPath.read(reissued.getContentAsString(), "$.accessToken");
		Cookie rotated = reissued.getCookie("refreshToken");

		// 7. 새 access도 통한다
		mockMvc.perform(get("/api/v1/users/irene").header("Authorization", "Bearer " + newAccessToken))
				.andExpect(status().isOk());

		// 8. 로그아웃하면 그 세션은 끝난다
		mockMvc.perform(post("/api/v1/auth/logout").cookie(rotated))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/reissue").cookie(rotated))
				.andExpect(status().isUnauthorized());
	}
}
