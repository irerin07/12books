package com.irene.twelvebooks.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

	/**
	 * 외부 호출에 타임아웃을 건다. 기본값은 무제한이라, 카카오가 응답하지 않으면 요청 스레드가
	 * 그대로 묶인다 — 검색 하나가 느려지는 게 아니라 톰캣 스레드 풀이 말라 나머지 API까지 멈춘다.
	 */
	@Bean
	public RestClient restClient() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(3));
		return RestClient.builder().requestFactory(requestFactory).build();
	}
}
