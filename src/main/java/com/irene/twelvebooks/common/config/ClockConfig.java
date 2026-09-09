package com.irene.twelvebooks.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

	/**
	 * 이 서비스의 기준 시간대. 한국 독자를 위한 서비스이고, "올해 몇 권 읽었나"가 제품의
	 * 핵심 숫자라 연도 경계가 사용자의 달력과 같아야 한다.
	 *
	 * <p>UTC로 두면 한국 시간 1월 1일 0시 30분에 완독한 책이 전년도 12월 31일로 저장되어
	 * 올해 목표에서 빠진다. 서버의 기본 시간대에 기대지 않고 여기서 못박는다.
	 */
	public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	/**
	 * 시간을 직접 읽는 대신 주입받는다. 토큰 만료처럼 시간에 의존하는 로직을
	 * 테스트에서 sleep 없이 검증하기 위해서다.
	 */
	@Bean
	public Clock clock() {
		return Clock.system(SERVICE_ZONE);
	}
}
