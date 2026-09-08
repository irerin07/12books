package com.irene.twelvebooks.book;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param maxConcurrentCalls 카카오를 동시에 부를 수 있는 최대 요청 수. 타임아웃만으로는
 *                           장애가 격리되지 않는다 — 카카오가 느려지면 검색 요청이 톰캣 스레드를
 *                           모두 차지해 로그인·조회까지 멈춘다. 상한을 넘으면 기다리지 않고 실패한다.
 */
@ConfigurationProperties(prefix = "twelvebooks.kakao")
public record KakaoProperties(String restApiKey, String baseUrl, int maxConcurrentCalls) {
}
