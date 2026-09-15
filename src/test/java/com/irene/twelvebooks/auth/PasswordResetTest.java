package com.irene.twelvebooks.auth;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비밀번호를 잊은 사람이 돌아오는 길.
 *
 * <p>없으면 운영자에게 연락하는 수밖에 없다 — 그건 서비스가 아니라 사람의 가용성에 기대는
 * 상태다.
 *
 * <p>테스트가 <b>진짜 SMTP 서버</b>(GreenMail)를 띄운다. 가짜 발송자를 끼워 넣고 "불렸는지"만
 * 보면 정작 중요한 것을 못 본다 — 메일이 실제로 조립돼 나갔는지, 본문에 링크가 제대로 들어갔는지.
 * 덕분에 <b>실제 발송자(Gmail·Resend…)를 아직 정하지 않아도</b> 기능을 끝까지 검증할 수 있다.
 */
@TestPropertySource(properties = {
		"spring.mail.host=localhost",
		"spring.mail.port=3025",
		"twelvebooks.auth.password-reset.link-base=https://12books.example/reset"
})
class PasswordResetTest extends AbstractIntegrationTest {

	@RegisterExtension
	static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		userRepository.save(User.create("me@example.com",
				passwordEncoder.encode("123456789"), "irene", "아이린"));
	}

	private void request(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/password-reset")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "%s"}""".formatted(email)))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("재설정을 요청하면 메일이 한 통 나가고 본문에 링크가 들어 있다")
	void sendsResetLink() throws Exception {
		request("me@example.com");

		assertThat(GREEN_MAIL.waitForIncomingEmail(5000, 1)).isTrue();
		MimeMessage sent = GREEN_MAIL.getReceivedMessages()[0];
		assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("me@example.com");
		// 링크가 없으면 메일을 받아도 할 수 있는 일이 없다.
		// 본문은 그대로 읽지 않는다 — 한글이 섞이면 base64로 실려 나가므로 원문과 비교하면
		// 기능이 멀쩡한데도 빨갛다. getContent()가 전송 인코딩을 풀어 준다.
		assertThat((String) sent.getContent()).contains("https://12books.example/reset?token=");
	}

	/** 메일 본문에서 토큰만 꺼낸다. 사용자가 링크를 누르는 것과 같은 경로다. */
	private String tokenFromMail() throws Exception {
		assertThat(GREEN_MAIL.waitForIncomingEmail(5000, 1)).isTrue();
		String body = (String) GREEN_MAIL.getReceivedMessages()[0].getContent();
		int at = body.indexOf("?token=") + "?token=".length();
		int end = at;
		while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
			end++;
		}
		return body.substring(at, end);
	}

	private MvcResult confirm(String token, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token": "%s", "password": "%s"}""".formatted(token, password)))
				.andReturn();
	}

	private MvcResult login(String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "me@example.com", "password": "%s"}""".formatted(password)))
				.andReturn();
	}

	@Test
	@DisplayName("메일의 링크로 비밀번호를 바꾸면 새 것으로 로그인되고 옛 것은 막힌다")
	void changesPassword() throws Exception {
		request("me@example.com");
		String token = tokenFromMail();

		assertThat(confirm(token, "newpassword1").getResponse().getStatus()).isEqualTo(204);

		assertThat(login("newpassword1").getResponse().getStatus()).isEqualTo(200);
		// 옛 비밀번호가 계속 통하면 바꾼 것이 아니다.
		assertThat(login("123456789").getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	@DisplayName("같은 링크를 두 번 쓸 수 없다")
	void tokenIsSingleUse() throws Exception {
		request("me@example.com");
		String token = tokenFromMail();
		confirm(token, "newpassword1");

		// 링크가 살아 있으면 메일함을 나중에 본 사람이 다시 계정을 가져갈 수 있다.
		mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token": "%s", "password": "otherpassword1"}""".formatted(token)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A005"));
	}

	/**
	 * 비밀번호를 바꾸는 이유는 보통 탈취다. 훔친 기기의 refresh가 그대로 살아 있으면
	 * 바꾼 의미가 없다.
	 */
	@Test
	@DisplayName("재설정하면 이미 로그인돼 있던 기기의 재발급이 끊긴다")
	void revokesExistingSessions() throws Exception {
		Cookie refresh = login("123456789").getResponse().getCookie(RefreshCookies.NAME);
		assertThat(refresh).isNotNull();

		request("me@example.com");
		confirm(tokenFromMail(), "newpassword1");

		mockMvc.perform(post("/api/v1/auth/reissue").cookie(refresh))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A003"));
	}

	@Test
	@DisplayName("없는 이메일도 204지만 메일은 나가지 않는다")
	void neverRevealsWhetherTheAccountExists() throws Exception {
		// "가입되지 않은 이메일입니다"로 답하면 그 한 줄이 계정 열거 통로가 된다.
		request("nobody@example.com");

		assertThat(GREEN_MAIL.waitForIncomingEmail(1000, 1)).isFalse();
	}
}
