package com.irene.twelvebooks.common.ratelimit;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;

/**
 * 한도를 넘었다.
 *
 * <p>다른 업무 예외와 달리 <b>언제 다시 오면 되는지</b>를 함께 들고 다닌다. 그 값이 없으면
 * 클라이언트가 계속 두드리게 되어, 막는 의미가 반감되고 서버 부하도 그대로 남는다.
 */
public class RateLimitExceededException extends BusinessException {

	private final long retryAfterSeconds;

	public RateLimitExceededException(long retryAfterSeconds) {
		super(ErrorCode.TOO_MANY_REQUESTS);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
