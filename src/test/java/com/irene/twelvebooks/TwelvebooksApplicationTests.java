package com.irene.twelvebooks;

import com.irene.twelvebooks.auth.JwtProperties;
import com.irene.twelvebooks.book.KakaoProperties;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TwelvebooksApplicationTests extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProperties jwtProperties;

	@Autowired
	KakaoProperties kakaoProperties;

	@Test
	@DisplayName("헬스 체크는 인증 없이 열려 있고 UP을 돌려준다")
	void healthIsUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("설정 프로퍼티가 twelvebooks 네임스페이스로 바인딩된다")
	void propertiesAreBound() {
		assertThat(jwtProperties.secret()).isNotBlank();
		assertThat(jwtProperties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(10));
		assertThat(jwtProperties.refreshTokenTtl()).isEqualTo(Duration.ofDays(14));
		assertThat(kakaoProperties.restApiKey()).isNotBlank();
		assertThat(kakaoProperties.baseUrl()).isEqualTo("https://dapi.kakao.com");
	}
}
