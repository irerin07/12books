package com.irene.twelvebooks.auth;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 링크는 시간이 지나면 죽는다.
 *
 * <p>수명을 <b>Redis의 만료</b>에 맡긴 것이 요점이다. 테이블에 두고 "만료 시각"을 비교하면
 * 지나간 토큰을 지우는 일이 따로 생기고, 그 청소를 잊으면 만료된 줄 알았던 링크가 살아 있다.
 *
 * <p>그래서 이 테스트는 시계를 가짜로 돌리지 않는다 — 실제로 기다린다. 만료를 Redis가
 * 한다는 사실 자체를 확인해야 하기 때문이고, 그 대신 수명을 1초로 줄여 둔다.
 */
@TestPropertySource(properties = {
		"spring.mail.host=localhost",
		"spring.mail.port=3026",
		"twelvebooks.auth.password-reset.ttl=PT1S"
})
class PasswordResetExpiryTest extends AbstractIntegrationTest {

	@RegisterExtension
	static final GreenMailExtension GREEN_MAIL =
			new GreenMailExtension(ServerSetupTest.SMTP.port(3026));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	PasswordResetTokenStore tokenStore;

	@Test
	@DisplayName("시간이 지난 링크는 통하지 않는다")
	void expiredTokenIsRejected() throws Exception {
		Long userId = userRepository.save(User.create("me@example.com",
				passwordEncoder.encode("123456789"), "irene", "아이린")).getId();

		// 메일을 거치지 않는다. 여기서 볼 것은 링크의 수명이지 발송이 아니다.
		String token = tokenStore.issue(userId);
		Thread.sleep(Duration.ofMillis(1500));

		mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token": "%s", "password": "newpassword1"}""".formatted(token)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A005"));

		// 비밀번호는 그대로여야 한다.
		assertThat(mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "me@example.com", "password": "123456789"}"""))
				.andReturn().getResponse().getStatus()).isEqualTo(200);
	}
}
