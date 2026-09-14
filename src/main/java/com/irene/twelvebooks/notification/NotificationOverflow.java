package com.irene.twelvebooks.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 대기열이 가득 찼을 때 알림을 버리고 그 사실을 남긴다.
 *
 * <p>버린 건마다 로그를 찍으면 <b>과부하일 때 로그 자체가 부하가 된다</b> — 정확히 시스템이
 * 힘들 때 가장 많이 쓰게 된다. 건수는 메트릭으로 누적하고, 사람이 읽는 경고는 창을 두고
 * 모아서 한 번만 낸다.
 *
 * <p>버리는 정책 자체는 그대로다. 알림 한 건을 잃는 것이 응답이 느려지거나 메모리가 차는
 * 것보다 낫고, {@code CallerRunsPolicy}는 요청 스레드에서 다시 실행되어 이 Phase에서 겪은
 * 커넥션 경합을 되살린다.
 */
public class NotificationOverflow implements RejectedExecutionHandler {

	/** 이 간격 안에 버린 것은 한 줄로 모아 낸다. */
	static final Duration REPORT_INTERVAL = Duration.ofSeconds(10);

	private static final Logger log = LoggerFactory.getLogger(NotificationOverflow.class);

	private final Counter dropped;
	private final Clock clock;
	private final AtomicLong sinceLastReport = new AtomicLong();
	private final AtomicLong nextReportAt = new AtomicLong();

	public NotificationOverflow(MeterRegistry meterRegistry, Clock clock) {
		this.dropped = Counter.builder("twelvebooks.notifications.dropped")
				.description("대기열 포화로 버린 알림 수")
				.register(meterRegistry);
		this.clock = clock;
	}

	@Override
	public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
		dropped.increment();
		long batch = sinceLastReport.incrementAndGet();

		long now = clock.millis();
		long due = nextReportAt.get();
		// 창을 연 스레드 하나만 로그를 낸다. 나머지는 다음 창으로 넘긴다.
		if (now >= due && nextReportAt.compareAndSet(due, now + REPORT_INTERVAL.toMillis())) {
			sinceLastReport.addAndGet(-batch);
			log.warn("알림 대기열이 가득 차 {}건을 버렸습니다. 활성 {} 대기 {}",
					batch, executor.getActiveCount(), executor.getQueue().size());
		}
	}

	/** 누적 폐기 수. 메트릭 엔드포인트가 열리기 전까지 확인할 길이 이것뿐이다. */
	public double droppedCount() {
		return dropped.count();
	}
}
