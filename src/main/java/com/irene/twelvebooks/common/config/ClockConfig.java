package com.irene.twelvebooks.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

	/**
	 * 시간을 직접 읽는 대신 주입받는다. 토큰 만료처럼 시간에 의존하는 로직을
	 * 테스트에서 sleep 없이 검증하기 위해서다.
	 */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
