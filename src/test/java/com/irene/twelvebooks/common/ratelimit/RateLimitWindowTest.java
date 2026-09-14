package com.irene.twelvebooks.common.ratelimit;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 창이 바뀌는 순간.
 *
 * <p>실패만 세는 경로는 <b>먼저 잡고 성공하면 돌려준다.</b> 잡은 때와 돌려주는 때 사이에 창이
 * 끝나면, 돌려주는 쪽이 새 창의 카운터를 깎을 수 있다 — 이전 창의 성공이 새 창의 실패
 * 횟수를 지워, 창 경계에 맞춰 요청을 흘리면 한도가 사실상 사라진다.
 *
 * <p>실제 시계로는 이 순간을 만들 수 없으니 시각을 우리가 쥔다.
 */
class RateLimitWindowTest extends AbstractIntegrationTest {

	private static final Duration WINDOW = Duration.ofSeconds(60);

	/** 우리가 움직이는 시계. */
	private static class MovableClock extends Clock {

		private Instant now = Instant.parse("2026-09-14T00:00:30Z");

		void advance(Duration amount) {
			now = now.plus(amount);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}

	@Autowired
	StringRedisTemplate redis;

	@Test
	@DisplayName("이전 창에서 잡은 자리를 돌려줘도 새 창의 횟수는 그대로다")
	void refundNeverTouchesAnotherWindow() {
		MovableClock clock = new MovableClock();
		RateLimiter limiter = new RateLimiter(redis, clock);

		RateLimiter.Decision reserved = limiter.check("login:ip:203.0.113.61", 20, WINDOW);
		assertThat(reserved.allowed()).isTrue();

		clock.advance(WINDOW);
		RateLimiter.Decision nextWindow = limiter.check("login:ip:203.0.113.61", 20, WINDOW);
		assertThat(nextWindow.key()).isNotEqualTo(reserved.key());

		// 이전 창의 요청이 이제야 성공해 자리를 돌려준다.
		limiter.refund(reserved.key());

		// 새 창은 한 건을 센 채로 남아야 한다. 여기서 0이 되면 창마다 한 건씩 공짜가 생긴다.
		assertThat(redis.opsForValue().get(nextWindow.key())).isEqualTo("1");
	}
}
