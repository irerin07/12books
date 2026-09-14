package com.irene.twelvebooks.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 커밋 뒤에 하는 일을 요청 스레드에서 떼어 낸다.
 *
 * <p>{@code @TransactionalEventListener(AFTER_COMMIT)}를 같은 스레드에서 {@code REQUIRES_NEW}로
 * 처리하면 <b>커밋 시점에 커넥션을 하나 더 요구한다</b> — 바깥 트랜잭션이 아직 자기 커넥션을
 * 쥔 채 콜백이 돌기 때문이다. 동시 요청 수가 풀 크기에 가까워지면 모든 스레드가 두 번째
 * 커넥션을 기다리며 서로를 막는다.
 *
 * <p>실제로 그렇게 만들었다가 동시 좋아요 10건에서 전부 멈췄다. 스레드를 나누면 요청 쪽이
 * 커넥션을 반납한 뒤에 알림이 돌므로 그 경합이 사라진다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
