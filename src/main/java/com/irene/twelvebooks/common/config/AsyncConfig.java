package com.irene.twelvebooks.common.config;

import com.irene.twelvebooks.notification.NotificationOverflow;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.Executor;

/**
 * 커밋 뒤에 하는 일을 요청 스레드에서 떼어 낸다.
 *
 * <p>{@code @TransactionalEventListener(AFTER_COMMIT)}를 같은 스레드에서 {@code REQUIRES_NEW}로
 * 처리하면 <b>커밋 시점에 커넥션을 하나 더 요구한다</b> — 바깥 트랜잭션이 아직 자기 커넥션을
 * 쥔 채 콜백이 돌기 때문이다. 동시 요청 수가 풀 크기에 가까워지면 모든 스레드가 두 번째
 * 커넥션을 기다리며 서로를 막는다. 실제로 그렇게 만들었다가 동시 좋아요 10건에서 전부 멈췄다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

	public static final String MAIL_EXECUTOR = "mailExecutor";

	/**
	 * 알림 전용 실행기.
	 *
	 * <p>기본 실행기를 쓰지 않는 이유는 <b>대기열에 상한이 없기 때문이다.</b> 반응 유입이 알림
	 * 저장 속도를 계속 넘거나 DB가 느려지면 할 일이 메모리에 무한정 쌓이고, 결국 알림 지연을
	 * 넘어 본 기능까지 끌고 내려간다. 부가 기능이 본 기능을 해치지 않게 하려고 스레드를 나눈
	 * 것인데 거기서 다시 묶이면 뜻이 없다.
	 *
	 * <p>넘치면 <b>버린다.</b> {@code CallerRunsPolicy}는 쓰지 않는다 — 요청 스레드에서 다시
	 * 실행되어 위에 적은 커넥션 경합을 그대로 되살린다. 알림 한 건을 잃는 것이 응답이 느려지거나
	 * 메모리가 차는 것보다 낫다. 버린 사실을 어떻게 남기는지는 {@link NotificationOverflow}에 있다.
	 *
	 * <p>종료할 때는 하던 일을 잠깐 기다린다. 배포마다 진행 중인 알림을 버릴 이유는 없다.
	 */
	@Bean(NOTIFICATION_EXECUTOR)
	public Executor notificationExecutor(MeterRegistry meterRegistry, Clock clock) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("notification-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(500);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		executor.setRejectedExecutionHandler(new NotificationOverflow(meterRegistry, clock));
		executor.initialize();

		// 얼마나 밀려 있는지를 밖에서 볼 수 있게 한다. 버린 건수만 세면 "버리기 직전"을
		// 알아챌 수 없어 손 쓸 시점을 놓친다.
		Gauge.builder("twelvebooks.notifications.queue.size", executor,
						e -> e.getThreadPoolExecutor().getQueue().size())
				.description("알림 대기열에 쌓인 수")
				.register(meterRegistry);
		Gauge.builder("twelvebooks.notifications.active", executor,
						ThreadPoolTaskExecutor::getActiveCount)
				.description("알림을 처리 중인 스레드 수")
				.register(meterRegistry);

		return executor;
	}

	/**
	 * 메일 전용 실행기.
	 *
	 * <p>알림과 나눈 이유는 <b>느려지는 방식이 다르기 때문이다.</b> 알림은 DB가 느려질 때
	 * 밀리고 메일은 남의 SMTP 서버가 느려질 때 밀린다. 한 풀을 같이 쓰면 메일 서버 하나가
	 * 느려졌을 뿐인데 알림이 통째로 멈춘다.
	 *
	 * <p>작게 잡는다. 비밀번호 재설정은 드문 행동이고, 여기 쌓일 정도면 이미 메일 서버가
	 * 죽은 것이다. 넘치면 버린다 — 사용자는 다시 요청하면 새 링크를 받는다. 버렸다는 사실은
	 * 로그로 남는다(기본 {@code AbortPolicy}가 던지는 예외를 {@code @Async}가 로그로 남긴다).
	 */
	@Bean(MAIL_EXECUTOR)
	public Executor mailExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("mail-");
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(100);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		executor.initialize();
		return executor;
	}
}
