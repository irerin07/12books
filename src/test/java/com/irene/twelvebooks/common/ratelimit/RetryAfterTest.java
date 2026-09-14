package com.irene.twelvebooks.common.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 남은 시간을 {@code Retry-After}에 실을 값으로 바꾸는 규칙.
 *
 * <p>경계가 셋이라 눈으로 지나치기 쉽다. 특히 {@code 0}은 정상 상태인데 이상 신호로 다루면,
 * 1초 뒤면 되는 사람에게 창 전체를 기다리라고 안내하게 된다.
 */
class RetryAfterTest {

	private static final Duration WINDOW = Duration.ofMinutes(5);

	@Test
	@DisplayName("남은 시간이 있으면 그대로 쓴다")
	void usesRemainingSeconds() {
		assertThat(RateLimiter.retryAfterFrom(42, WINDOW)).isEqualTo(42);
	}

	@Test
	@DisplayName("0은 곧 풀린다는 뜻이므로 1초로 안내한다")
	void treatsZeroAsAlmostOver() {
		// 창 전체(300초)로 바꾸면 1초 뒤면 되는 사람에게 5분을 기다리라고 하게 된다.
		assertThat(RateLimiter.retryAfterFrom(0, WINDOW)).isEqualTo(1);
	}

	@Test
	@DisplayName("음수는 이상 신호라 창 길이를 준다")
	void fallsBackOnNegativeTtl() {
		// -1은 만료가 안 걸린 키, -2는 그사이 사라진 키다. 둘 다 그대로 실으면
		// 클라이언트가 해석할 수 없는 값이 된다.
		assertThat(RateLimiter.retryAfterFrom(-1, WINDOW)).isEqualTo(300);
		assertThat(RateLimiter.retryAfterFrom(-2, WINDOW)).isEqualTo(300);
	}
}
