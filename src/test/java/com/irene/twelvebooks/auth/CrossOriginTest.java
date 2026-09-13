package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프런트가 <b>다른 도메인</b>에서 API를 부르는 경우.
 *
 * <p>지금까지는 같은 오리진(검증 콘솔)에서만 불러서 드러나지 않았다. 프런트가 따로 뜨는
 * 순간 브라우저가 preflight부터 막고, 요청은 서버에 닿지도 않는다.
 *
 * <p>허용 목록을 코드에 박지 않고 설정으로 받는다 — 프런트 주소는 배포 환경마다 다르고
 * 미리보기 배포는 주소가 계속 바뀐다. 박아 두면 그때마다 서버를 다시 배포해야 한다.
 */
@TestPropertySource(properties = {
		"twelvebooks.cors.allowed-origins=https://qa.twelvebooks.example,http://localhost:5173",
		"twelvebooks.auth.refresh-cookie-same-site=None"
})
class CrossOriginTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	RefreshCookies refreshCookies;

	@Test
	@DisplayName("허용한 오리진의 preflight는 통과하고 자격증명 동반을 허락한다")
	void allowsConfiguredOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/posts")
						.header(HttpHeaders.ORIGIN, "https://qa.twelvebooks.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"https://qa.twelvebooks.example"))
				// refresh 쿠키가 오가려면 이것이 있어야 한다. 없으면 재발급이 조용히 실패한다.
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	@DisplayName("목록에 없는 오리진은 preflight에서 막힌다")
	void rejectsUnknownOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/posts")
						.header(HttpHeaders.ORIGIN, "https://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("실제 요청에도 허용 오리진 헤더가 실린다 — preflight만 통과시키면 본 요청이 막힌다")
	void putsHeaderOnActualResponse() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"nobody@example.com","password":"123456789"}"""))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"http://localhost:5173"));
	}

	@Test
	@DisplayName("refresh 쿠키의 SameSite는 설정으로 정한다")
	void refreshCookieSameSiteIsConfigurable() {
		HttpHeaders headers = new HttpHeaders();
		refreshCookies.set(headers, "some-refresh-token");

		String cookie = headers.getFirst(HttpHeaders.SET_COOKIE);
		// 프런트가 다른 사이트면 Strict인 쿠키는 아예 붙지 않아 재발급이 10분마다 끊긴다.
		// 대신 None은 Secure를 반드시 동반해야 브라우저가 받는다.
		assertThat(cookie).contains("SameSite=None").contains("Secure").contains("HttpOnly");
	}
}
