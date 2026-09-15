package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 재설정과 <b>진행 중이던 로그인</b>이 부딪히는 경우.
 *
 * <p>세션을 전부 끊어도 그 순간 날아오던 로그인은 잡히지 않는다. 옛 비밀번호로 이미 검증을
 * 통과한 요청이 무효화가 끝난 <b>뒤에</b> 세션을 만들기 때문이다. 그 세션은 끊긴 적이 없으므로
 * 계속 재발급된다 — <b>비밀번호를 바꿨는데 옛 비밀번호를 아는 사람이 그대로 남는다.</b>
 *
 * <p>access 토큰의 남은 수명을 허용하는 것과는 다른 문제다. 그쪽은 몇 분이면 끝나지만
 * 이쪽은 refresh가 이어지는 한 끝나지 않는다.
 *
 * <p><b>순서를 시간이 아니라 걸쇠로 만든다.</b> sleep으로 창을 벌리면 그 테스트는 "그때
 * 그렇게 됐다"만 말할 뿐, 통과했다고 모든 실행 순서가 안전하다는 뜻이 되지 못한다. 여기서는
 * 비밀번호 검증 안에서 로그인을 멈춰 세우고, 그사이에 재설정을 <b>끝까지</b> 돌린 뒤 풀어 준다.
 */
@TestPropertySource(properties = {
		"spring.main.allow-bean-definition-overriding=true"
})
@Import(PasswordResetRaceTest.GatedEncoder.class)
class PasswordResetRaceTest extends AbstractIntegrationTest {

	/**
	 * 비밀번호 검증 안에서 한 번 멈추는 인코더. 멈추는 것은 {@code matches}뿐이라
	 * 재설정의 {@code encode}는 그대로 돈다.
	 */
	static class Gate implements PasswordEncoder {

		private final PasswordEncoder delegate = new BCryptPasswordEncoder();

		final CountDownLatch entered = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);

		@Override
		public String encode(CharSequence rawPassword) {
			return delegate.encode(rawPassword);
		}

		@Override
		public boolean matches(CharSequence rawPassword, String encodedPassword) {
			entered.countDown();
			try {
				release.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			// 멈춰 있는 동안 비밀번호가 바뀌어도, 검증하는 값은 로그인이 읽어 둔 옛 해시다.
			return delegate.matches(rawPassword, encodedPassword);
		}
	}

	@TestConfiguration
	static class GatedEncoder {

		@Bean
		Gate passwordEncoder() {
			return new Gate();
		}
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	Gate gate;

	@Autowired
	PasswordResetTokenStore tokenStore;

	@Test
	@DisplayName("무효화가 끝난 뒤에 만들어진 세션도 재발급되지 않는다")
	void sessionIssuedDuringResetIsDead() throws Exception {
		Long userId = userRepository.save(User.create("me@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "irene", "아이린")).getId();
		String token = tokenStore.issue(userId);

		// 1. 로그인이 옛 해시를 읽고 검증에 들어가 멈춘다.
		CompletableFuture<MvcResult> login = CompletableFuture.supplyAsync(() -> {
			try {
				return mockMvc.perform(post("/api/v1/auth/login")
								.contentType(MediaType.APPLICATION_JSON)
								.content("""
										{"email": "me@example.com", "password": "123456789"}"""))
						.andReturn();
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();

		// 2. 그사이 재설정이 끝까지 돈다 — 비밀번호 교체와 세션 무효화까지.
		mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token": "%s", "password": "newpassword1"}""".formatted(token)))
				.andExpect(status().isNoContent());

		// 3. 이제 로그인이 세션을 만든다. 무효화는 이미 끝났으므로 이 세션은 대상이 아니었다.
		gate.release.countDown();
		MvcResult result = login.join();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		Cookie refresh = result.getResponse().getCookie(RefreshCookies.NAME);
		assertThat(refresh).isNotNull();

		// 옛 비밀번호로 만들어진 세션이다. 살아 있으면 재설정이 뚫린 것이다.
		mockMvc.perform(post("/api/v1/auth/reissue").cookie(refresh))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A003"));
	}
}
