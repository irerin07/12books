package com.irene.twelvebooks.notification;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 대기열이 넘칠 때의 기록 방식.
 *
 * <p>버린 건마다 로그를 찍으면 과부하일 때 로그 자체가 부하가 된다 — 시스템이 가장 힘들 때
 * 가장 많이 쓰게 된다. 그렇다고 조용히 버리면 "알림이 왜 안 왔지"에 답할 수 없다.
 *
 * <p>그래서 <b>건수는 빠짐없이 세고, 사람이 읽는 경고만 모아서</b> 낸다. 이 테스트는 앞쪽을
 * 지킨다 — 로그를 줄이려다 세는 것까지 놓치면 관측이 사라진다.
 */
class NotificationOverflowTest {

	/** 시각을 우리가 쥔다. 실제 시계로는 "창이 안 지났다"를 만들 수 없다. */
	private static class MovableClock extends Clock {

		private Instant now = Instant.parse("2026-09-14T00:00:00Z");

		void advance(java.time.Duration amount) {
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

	private final ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(1));

	@Test
	@DisplayName("버린 건수는 한 건도 빠뜨리지 않고 센다")
	void countsEveryDrop() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		NotificationOverflow overflow = new NotificationOverflow(registry, new MovableClock());

		for (int i = 0; i < 100; i++) {
			overflow.rejectedExecution(() -> {
			}, pool);
		}

		// 로그는 창마다 한 번이지만 숫자는 전부 남아야 한다. 이것이 없으면 과부하가
		// 지나간 뒤 얼마나 잃었는지 알 방법이 없다.
		assertThat(overflow.droppedCount()).isEqualTo(100);
	}

	@Test
	@DisplayName("버리는 것이 호출자에게 예외를 던지지 않는다")
	void neverThrows() {
		NotificationOverflow overflow =
				new NotificationOverflow(new SimpleMeterRegistry(), new MovableClock());

		// 여기서 예외가 나가면 알림을 버리려다 반응 자체를 깨뜨린다.
		assertThatNoException().isThrownBy(() -> overflow.rejectedExecution(() -> {
		}, pool));
	}

	@Test
	@DisplayName("시간이 지나면 다시 한 번 보고한다")
	void reportsAgainAfterTheWindow() {
		MovableClock clock = new MovableClock();
		NotificationOverflow overflow = new NotificationOverflow(new SimpleMeterRegistry(), clock);

		overflow.rejectedExecution(() -> {
		}, pool);
		clock.advance(NotificationOverflow.REPORT_INTERVAL.plusSeconds(1));
		overflow.rejectedExecution(() -> {
		}, pool);

		// 창이 지난 뒤에도 세는 것은 그대로다. 창은 로그 빈도만 줄인다.
		assertThat(overflow.droppedCount()).isEqualTo(2);
	}
}
