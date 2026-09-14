package com.irene.twelvebooks.notification;

import com.irene.twelvebooks.common.config.AsyncConfig;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림을 돌리는 실행기의 성질.
 *
 * <p>스레드를 나눈 것은 부가 기능이 본 기능을 붙잡지 않게 하려는 것이었다. 그런데 대기열에
 * 상한이 없으면 밀린 일이 메모리에 무한정 쌓여, 결국 본 기능까지 끌고 내려간다 —
 * 떼어 놓은 의미가 사라진다.
 *
 * <p>Spring Boot의 기본 실행기가 바로 그렇다. 누가 {@code @Async}에서 이름을 지우면 조용히
 * 그쪽으로 돌아가고, 그 사실은 터지기 전까지 아무도 모른다. 그래서 상한을 테스트로 박는다.
 */
class NotificationExecutorTest extends AbstractIntegrationTest {

	@Autowired
	@Qualifier(AsyncConfig.NOTIFICATION_EXECUTOR)
	Executor notificationExecutor;

	@Test
	@DisplayName("알림 실행기는 스레드와 대기열에 상한이 있다")
	void isBounded() {
		assertThat(notificationExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
		ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) notificationExecutor;

		assertThat(executor.getMaxPoolSize()).isLessThan(Integer.MAX_VALUE);
		// 대기열 상한이 핵심이다. 스레드만 막고 대기열을 열어 두면 쌓이는 곳만 옮겨진다.
		assertThat(executor.getQueueCapacity()).isLessThan(Integer.MAX_VALUE);
	}
}
