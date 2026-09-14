package com.irene.twelvebooks.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

	private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

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
	 * 메모리가 차는 것보다 낫다. 버린 사실은 로그로 남긴다.
	 *
	 * <p>종료할 때는 하던 일을 잠깐 기다린다. 배포마다 진행 중인 알림을 버릴 이유는 없다.
	 */
	@Bean(NOTIFICATION_EXECUTOR)
	public Executor notificationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("notification-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(500);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		executor.setRejectedExecutionHandler((task, pool) ->
				log.warn("알림 대기열이 가득 차 한 건을 버립니다. 활성 {} 대기 {}",
						pool.getActiveCount(), pool.getQueue().size()));
		executor.initialize();
		return executor;
	}
}
