package com.irene.twelvebooks.auth;

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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends AbstractIntegrationTest {

	private static final String REFRESH_COOKIE = "refreshToken";

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

	private MvcResult signupAndLogin() throws Exception {
		signup("irene@example.com", "password123", "irene");
		return mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"irene@example.com","password":"password123"}"""))
				.andExpect(status().isOk())
				.andReturn();
	}

	private void signup(String email, String password, String handle) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s","handle":"%s","displayName":"아이린"}"""
								.formatted(email, password, handle)))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("가입하면 201이고 응답에 비밀번호가 실리지 않는다")
	void signsUp() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"irene@example.com","password":"password123","handle":"irene","displayName":"아이린"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.handle").value("irene"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		assertThat(userRepository.findByEmail("irene@example.com"))
				.get()
				.satisfies(user -> {
					assertThat(user.getPasswordHash()).isNotEqualTo("password123");
					assertThat(user.getPasswordHash()).startsWith("$2");
				});
	}

	@Test
	@DisplayName("형식이 틀리면 400과 함께 어느 필드가 틀렸는지 알려준다")
	void rejectsInvalidSignup() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"이메일아님","password":"short","handle":"BAD handle","displayName":""}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"))
				.andExpect(jsonPath("$.fieldErrors.length()").value(4));
	}

	@Test
	@DisplayName("이메일이 중복이면 409")
	void rejectsDuplicateEmail() throws Exception {
		signup("dup@example.com", "password123", "handle1");

		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"dup@example.com","password":"password123","handle":"handle2","displayName":"아이린"}"""))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("handle이 중복이면 409")
	void rejectsDuplicateHandle() throws Exception {
		signup("a@example.com", "password123", "samehandle");

		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"b@example.com","password":"password123","handle":"samehandle","displayName":"아이린"}"""))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("로그인하면 access는 본문, refresh는 HttpOnly 쿠키로 온다")
	void logsIn() throws Exception {
		MvcResult result = signupAndLogin();

		assertThat(result.getResponse().getContentAsString()).contains("accessToken");
		Cookie refresh = result.getResponse().getCookie(REFRESH_COOKIE);
		assertThat(refresh).isNotNull();
		assertThat(refresh.isHttpOnly()).isTrue();
		assertThat(refresh.getSecure()).isTrue();
		assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
		assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict");
	}

	@Test
	@DisplayName("없는 이메일과 틀린 비밀번호는 같은 401을 돌려준다 — 계정이 있는지 알려주지 않는다")
	void doesNotRevealWhetherAccountExists() throws Exception {
		signup("irene@example.com", "password123", "irene");

		String wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"irene@example.com","password":"wrongpassword"}"""))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		String unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"nobody@example.com","password":"password123"}"""))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		assertThat(wrongPassword).isEqualTo(unknownEmail);
	}

	@Test
	@DisplayName("refresh 쿠키로 재발급하면 새 access와 새 refresh 쿠키를 받고 옛 refresh는 죽는다")
	void reissuesAndRotates() throws Exception {
		Cookie refresh = signupAndLogin().getResponse().getCookie(REFRESH_COOKIE);

		Cookie rotated = mockMvc.perform(post("/api/v1/auth/reissue").cookie(refresh))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn().getResponse().getCookie(REFRESH_COOKIE);

		assertThat(rotated).isNotNull();
		assertThat(rotated.getValue()).isNotEqualTo(refresh.getValue());

		mockMvc.perform(post("/api/v1/auth/reissue").cookie(refresh))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("refresh 쿠키가 없으면 재발급은 401")
	void rejectsReissueWithoutCookie() throws Exception {
		mockMvc.perform(post("/api/v1/auth/reissue"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("로그아웃하면 쿠키가 지워지고 같은 refresh로는 재발급되지 않는다")
	void logsOut() throws Exception {
		Cookie refresh = signupAndLogin().getResponse().getCookie(REFRESH_COOKIE);

		mockMvc.perform(post("/api/v1/auth/logout").cookie(refresh))
				.andExpect(status().isNoContent())
				.andExpect(cookie().maxAge(REFRESH_COOKIE, 0));

		mockMvc.perform(post("/api/v1/auth/reissue").cookie(refresh))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("두 기기에서 로그인한 뒤 한쪽만 로그아웃해도 다른 쪽은 계속 쓴다")
	void keepsOtherDeviceSignedIn() throws Exception {
		signup("irene@example.com", "password123", "irene");

		Cookie phone = login().getResponse().getCookie(REFRESH_COOKIE);
		Cookie laptop = login().getResponse().getCookie(REFRESH_COOKIE);

		mockMvc.perform(post("/api/v1/auth/logout").cookie(phone))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/reissue").cookie(phone))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/reissue").cookie(laptop))
				.andExpect(status().isOk());
	}

	private MvcResult login() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"irene@example.com","password":"password123"}"""))
				.andExpect(status().isOk())
				.andReturn();
	}
}
