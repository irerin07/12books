package com.irene.twelvebooks;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 0~2를 브라우저에서 눌러 확인하는 정적 콘솔.
 *
 * <p>토큰을 받기 전에 열려야 하므로 인증 없이 접근할 수 있어야 한다. 반대로 이 예외가
 * API까지 새면 안 된다 — 두 가지를 함께 지킨다.
 */
class DevConsoleTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("검증 콘솔은 토큰 없이 열린다")
	void servesConsoleWithoutToken() throws Exception {
		// 루트는 웰컴 페이지가 /index.html로 포워드한다. MockMvc는 포워드를 따라가지 않으므로
		// 여기서는 열린다는 것만 보고, 내용은 실제 리소스 경로에서 확인한다.
		mockMvc.perform(get("/"))
				.andExpect(status().isOk());

		// 정적 리소스 응답에는 charset이 붙지 않아 MockMvc가 ISO-8859-1로 디코드한다.
		// 브라우저는 <meta charset>을 보고 UTF-8로 읽으므로, 여기서도 바이트를 UTF-8로 본다.
		mockMvc.perform(get("/index.html"))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
						.contains("12books 검증 콘솔"));
	}

	@Test
	@DisplayName("콘솔을 열어준다고 API까지 열리지는 않는다")
	void stillProtectsTheApi() throws Exception {
		mockMvc.perform(get("/api/v1/books/1"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/users/irene"))
				.andExpect(status().isUnauthorized());
	}
}
