package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.common.config.AsyncConfig;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.Executor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메일 대기열이 가득 찼을 때.
 *
 * <p>여기가 무너지면 <b>"언제나 204"라는 계약이 계정 유무를 흘리는 통로로 바뀐다.</b> 대기열이
 * 찬 상태에서 가입된 주소는 제출이 거절되며 500이 되고, 없는 주소는 제출 자체를 하지 않아
 * 204가 된다. 응답 코드만 보고도 누가 이 서비스를 쓰는지 알 수 있다.
 *
 * <p>거절 예외는 {@code @Async} 메서드 <b>안</b>이 아니라 제출하는 쪽에서 난다. 그래서 메서드
 * 안의 {@code try}로는 잡히지 않는다 — 부르는 자리에서 막아야 한다.
 */
@TestPropertySource(properties = {
		"spring.main.allow-bean-definition-overriding=true"
})
@Import(PasswordResetMailOverflowTest.AlwaysFullExecutor.class)
class PasswordResetMailOverflowTest extends AbstractIntegrationTest {

	/** 언제나 가득 찬 실행기. 포화를 만들려고 실제로 채우면 테스트가 타이밍에 기댄다. */
	@TestConfiguration
	static class AlwaysFullExecutor {

		@Bean(AsyncConfig.MAIL_EXECUTOR)
		Executor mailExecutor() {
			return task -> {
				throw new TaskRejectedException("대기열이 가득 찼습니다");
			};
		}
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	@DisplayName("메일을 제출하지 못해도 가입된 주소와 아닌 주소가 똑같이 204다")
	void staysSilentWhenTheMailQueueIsFull() throws Exception {
		userRepository.save(User.create("me@example.com",
				passwordEncoder.encode("123456789"), "irene", "아이린"));

		request("me@example.com");
		request("nobody@example.com");
	}

	private void request(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/password-reset")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "%s"}""".formatted(email)))
				.andExpect(status().isNoContent());
	}
}
