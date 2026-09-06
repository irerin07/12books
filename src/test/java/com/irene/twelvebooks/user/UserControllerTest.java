package com.irene.twelvebooks.user;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private User irene;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		irene = userRepository.save(User.create("irene@example.com", "$2a$10$hash", "irene", "아이린"));
	}

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(irene.getId(), irene.getHandle());
	}

	@Test
	@DisplayName("handle로 프로필을 조회한다")
	void readsProfile() throws Exception {
		mockMvc.perform(get("/api/v1/users/irene").header("Authorization", bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.handle").value("irene"))
				.andExpect(jsonPath("$.displayName").value("아이린"));
	}

	@Test
	@DisplayName("프로필 응답에 email과 비밀번호 해시는 실리지 않는다")
	void neverExposesCredentials() throws Exception {
		mockMvc.perform(get("/api/v1/users/irene").header("Authorization", bearer()))
				.andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	@DisplayName("없는 handle은 404")
	void returnsNotFoundForUnknownHandle() throws Exception {
		mockMvc.perform(get("/api/v1/users/nobody").header("Authorization", bearer()))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("토큰 없이 프로필을 조회하면 401")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/users/irene"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("PATCH /me로 내 프로필을 고친다")
	void updatesOwnProfile() throws Exception {
		mockMvc.perform(patch("/api/v1/me")
						.header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"displayName":"아이린2","bio":"책 읽는 사람","avatarUrl":"https://example.com/a.png"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("아이린2"))
				.andExpect(jsonPath("$.bio").value("책 읽는 사람"));

		assertThat(userRepository.findByHandle("irene")).get()
				.satisfies(user -> assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/a.png"));
	}

	@Test
	@DisplayName("보내지 않은 필드는 그대로 둔다")
	void leavesOmittedFieldsUntouched() throws Exception {
		mockMvc.perform(patch("/api/v1/me")
						.header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bio":"소개만 고친다"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("아이린"))
				.andExpect(jsonPath("$.bio").value("소개만 고친다"));
	}

	@Test
	@DisplayName("handle과 email은 PATCH /me로 바꿀 수 없다")
	void ignoresAttemptToChangeIdentity() throws Exception {
		mockMvc.perform(patch("/api/v1/me")
						.header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"handle":"stolen","email":"other@example.com","displayName":"아이린3"}"""))
				.andExpect(status().isOk());

		assertThat(userRepository.findByHandle("irene")).isPresent();
		assertThat(userRepository.findByHandle("stolen")).isEmpty();
		assertThat(userRepository.findByEmail("irene@example.com")).isPresent();
	}

	@Test
	@DisplayName("길이 제한을 넘으면 400")
	void rejectsTooLongValues() throws Exception {
		mockMvc.perform(patch("/api/v1/me")
						.header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"displayName":"%s"}""".formatted("가".repeat(51))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"));
	}

	@Test
	@DisplayName("토큰 없이 PATCH /me는 401")
	void requiresAuthenticationForUpdate() throws Exception {
		mockMvc.perform(patch("/api/v1/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"displayName":"아무개"}"""))
				.andExpect(status().isUnauthorized());
	}
}
