package com.irene.twelvebooks.user;

import com.irene.twelvebooks.auth.RefreshCookies;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탈퇴와 <b>진행 중이던 로그인</b>이 부딪히는 경우.
 *
 * <p>비밀번호 재설정에서 겪은 것과 같은 모양이다. 탈퇴 전 사용자를 읽은 로그인이 무효화가
 * 끝난 <b>뒤에</b> 세션을 만들면, 그 세션은 끊긴 적이 없어 계속 재발급된다.
 *
 * <p>다만 그때 도입한 <b>자격증명 지문은 이것을 잡지 못한다.</b> 탈퇴는 비밀번호를 바꾸지
 * 않으므로 지문이 그대로이고, 재발급의 사용자 조회도 탈퇴자를 돌려준다. access가 잠깐 남는
 * 문제가 아니라 <b>refresh를 계속 갱신할 수 있는</b> 문제다.
 *
 * <p>순서를 시간이 아니라 걸쇠로 만든다 — 비밀번호 검증 안에서 로그인을 멈춰 세우고,
 * 그사이 탈퇴를 끝까지 돌린 뒤 풀어 준다.
 */
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@Import(WithdrawalRaceTest.GatedEncoder.class)
class WithdrawalRaceTest extends AbstractIntegrationTest {

	/** 비밀번호 검증 안에서 한 번 멈추는 인코더. 탈퇴 쪽 검증까지 멈추지 않도록 한 번만 건다. */
	static class Gate implements PasswordEncoder {

		private final PasswordEncoder delegate = new BCryptPasswordEncoder();

		final CountDownLatch entered = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);

		private volatile boolean armed = true;

		@Override
		public String encode(CharSequence rawPassword) {
			return delegate.encode(rawPassword);
		}

		@Override
		public boolean matches(CharSequence rawPassword, String encodedPassword) {
			if (armed) {
				armed = false;
				entered.countDown();
				try {
					release.await(10, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
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
	com.irene.twelvebooks.auth.JwtProvider jwtProvider;

	@Test
	@DisplayName("탈퇴가 끝난 뒤에 만들어진 세션도 재발급되지 않는다")
	void sessionIssuedDuringWithdrawalIsDead() throws Exception {
		User me = userRepository.save(User.create("me@example.com",
				new BCryptPasswordEncoder().encode("123456789"), "irene", "아이린"));
		String bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());

		// 1. 로그인이 탈퇴 전 사용자를 읽고 검증에 들어가 멈춘다.
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

		// 2. 그사이 탈퇴가 끝까지 돈다 — 표시와 세션 무효화까지.
		mockMvc.perform(delete("/api/v1/me").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"password": "123456789"}"""))
				.andExpect(status().isNoContent());

		// 3. 이제 로그인이 세션을 만든다. 무효화는 이미 끝났으므로 이 세션은 대상이 아니었다.
		gate.release.countDown();
		MvcResult result = login.join();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		Cookie refresh = result.getResponse().getCookie(RefreshCookies.NAME);
		assertThat(refresh).isNotNull();

		// 탈퇴한 계정의 세션이다. 살아 있으면 탈퇴가 뚫린 것이다.
		mockMvc.perform(post("/api/v1/auth/reissue").cookie(refresh))
				.andExpect(status().isUnauthorized());
	}
}
