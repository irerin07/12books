package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * 아무것도 설정하지 않았을 때의 기본값.
 *
 * <p>허용 오리진을 비워 두면 <b>아무 오리진도 열리지 않는다.</b> 설정을 깜빡한 것이
 * 전면 개방으로 이어지면 안 되고, 편의를 위해 개발 주소를 기본값으로 넣어 두면 그 값이
 * 그대로 배포까지 따라간다.
 *
 * <p>{@code CrossOriginTest}가 "열었을 때 열리는지"를 보고 여기가 "안 열었을 때 닫혀
 * 있는지"를 본다. 뒤엣것이 없으면 앞엣것은 전면 개방으로도 통과한다.
 */
class CorsClosedByDefaultTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	RefreshCookies refreshCookies;

	@Test
	@DisplayName("허용 오리진을 설정하지 않으면 어떤 오리진도 열리지 않는다")
	void deniesEveryOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/posts")
						.header(HttpHeaders.ORIGIN, "https://anywhere.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	@DisplayName("refresh 쿠키는 기본이 SameSite=Strict다")
	void refreshCookieDefaultsToStrict() {
		HttpHeaders headers = new HttpHeaders();
		refreshCookies.set(headers, "some-refresh-token");

		// 낮추는 것은 배포 설정에서 명시적으로 한다. 기본이 느슨하면 아무도 알아채지 못한다.
		assertThat(headers.getFirst(HttpHeaders.SET_COOKIE)).contains("SameSite=Strict");
	}
}
