package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 재설정과 <b>진행 중이던 로그인</b>이 부딪히는 경우.
 *
 * <p>세션을 전부 끊어도 그 순간 날아오던 로그인은 잡히지 않는다. 옛 비밀번호로 이미 검증을
 * 통과한 요청이, 무효화가 끝난 <b>뒤에</b> 세션을 만들기 때문이다. 그 세션은 끊긴 적이 없으므로
 * 계속 재발급된다 — <b>비밀번호를 바꿨는데 옛 비밀번호를 아는 사람이 그대로 남는다.</b>
 *
 * <p>access 토큰의 남은 수명을 허용하는 것과는 다른 문제다. 그쪽은 몇 분이면 끝나지만
 * 이쪽은 refresh가 이어지는 한 끝나지 않는다.
 *
 * <p>BCrypt 검증이 100ms 안팎이라 창이 넓다. 재설정을 먼저 시작하고 로그인을 조금 뒤에
 * 던지면, 로그인은 옛 해시를 보고 통과한 뒤 무효화가 끝난 다음에 세션을 만든다.
 */
@TestPropertySource(properties = {
		"spring.mail.host=localhost",
		"spring.mail.port=3028"
})
class PasswordResetRaceTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	PasswordResetTokenStore tokenStore;

	@Test
	@DisplayName("무효화 직후에 만들어진 세션도 재발급되지 않는다")
	void sessionIssuedDuringResetIsDead() throws Exception {
		Long userId = userRepository.save(User.create("me@example.com",
				passwordEncoder.encode("123456789"), "irene", "아이린")).getId();
		String token = tokenStore.issue(userId);

		// 재설정을 먼저 띄운다. 토큰 소모 → 새 해시 계산(BCrypt) → 세션 전부 무효화 순으로 돈다.
		CompletableFuture<Void> reset = CompletableFuture.runAsync(() -> {
			try {
				mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
								.contentType(MediaType.APPLICATION_JSON)
								.content("""
										{"token": "%s", "password": "newpassword1"}""".formatted(token)))
						.andExpect(status().isNoContent());
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});

		// 무효화가 끝나기 전에 옛 비밀번호로 검증을 시작한다.
		Thread.sleep(40);
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "me@example.com", "password": "123456789"}"""))
				.andReturn();
		reset.join();

		// 이 순서가 만들어지지 않았다면(로그인이 더 늦게 읽었다면) 검사할 것이 없다.
		assertThat(login.getResponse().getStatus())
				.as("옛 비밀번호 로그인이 재설정 커밋 전에 검증을 통과해야 이 경합이 성립한다")
				.isEqualTo(200);
		Cookie refresh = login.getResponse().getCookie(RefreshCookies.NAME);
		assertThat(refresh).isNotNull();

		// 옛 비밀번호로 만들어진 세션이다. 살아 있으면 재설정이 뚫린 것이다.
		mockMvc.perform(post("/api/v1/auth/reissue").cookie(refresh))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A003"));
	}
}
