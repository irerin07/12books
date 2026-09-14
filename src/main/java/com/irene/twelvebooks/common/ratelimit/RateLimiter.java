package com.irene.twelvebooks.common.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 고정 창 카운터. 창이 시작될 때 1부터 세고, 창이 끝나면 키가 사라져 처음부터 다시 센다.
 *
 * <p>Redis가 이미 refresh 세션을 들고 있어 <b>새 인프라 없이</b> 된다. 슬라이딩 윈도가 더
 * 정밀하지만 요청마다 기록을 쌓아야 하고, 지금 필요한 것은 "스크립트가 두드리는 것을 막는"
 * 수준이라 고정 창으로 충분하다.
 *
 * <p>세는 것과 만료를 <b>한 스크립트로</b> 한다. 나눠 부르면 INCR과 EXPIRE 사이에서 죽었을 때
 * 만료 없는 키가 남아 그 사람이 영원히 막힌다 — 드물지만 한 번 생기면 스스로 풀리지 않는다.
 */
@Component
public class RateLimiter {

	/**
	 * 세고, 처음이면 만료를 걸고, 남은 시간을 함께 돌려준다.
	 *
	 * <p>남은 시간을 같은 호출에서 받는 이유는 {@code Retry-After}에 쓰기 위해서다. 따로
	 * 물으면 왕복이 하나 늘고, 그 사이에 창이 끝나면 음수가 나온다.
	 */
	private static final RedisScript<List> COUNT_AND_EXPIRE = new DefaultRedisScript<>("""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
			  redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			return { count, redis.call('TTL', KEYS[1]) }
			""", List.class);

	/** 돌려주되 0 아래로는 내려가지 않는다. */
	private static final RedisScript<Long> REFUND = new DefaultRedisScript<>("""
			local current = tonumber(redis.call('GET', KEYS[1]) or '0')
			if current > 0 then
			  return redis.call('DECR', KEYS[1])
			end
			return 0
			""", Long.class);

	private final StringRedisTemplate redis;

	public RateLimiter(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/**
	 * 한 번 쓰고 남은 여유를 돌려준다.
	 *
	 * @param bucket 버킷 이름과 대상을 합친 키. 엔드포인트마다 달라야 한다
	 * @param limit  한 창 안에 허용할 횟수
	 * @param window 창의 길이
	 */
	public Decision check(String bucket, int limit, Duration window) {
		List<?> result = redis.execute(COUNT_AND_EXPIRE, List.of(key(bucket)),
				String.valueOf(window.toSeconds()));
		long count = ((Number) result.get(0)).longValue();
		long ttl = ((Number) result.get(1)).longValue();

		return new Decision(count <= limit, retryAfterFrom(ttl, window));
	}

	/**
	 * 남은 시간을 {@code Retry-After}에 실을 수 있는 값으로 바꾼다.
	 *
	 * <p>{@code 0}은 <b>곧 풀린다</b>는 뜻이지 오류가 아니다. 그것을 창 전체로 바꾸면 1초 뒤면
	 * 되는 사람에게 5분을 기다리라고 안내하게 된다. 음수만 이상 신호로 보고 창 길이를 준다 —
	 * {@code -1}은 만료가 안 걸린 키, {@code -2}는 그사이 사라진 키다.
	 */
	static long retryAfterFrom(long ttl, Duration window) {
		if (ttl > 0) {
			return ttl;
		}
		return ttl == 0 ? 1 : window.toSeconds();
	}

	/**
	 * 세었던 한 번을 돌려준다. 실패만 세는 경로가 <b>성공했을 때</b> 부른다.
	 *
	 * <p>"보고 나서 센다"가 아니라 "먼저 세고 성공하면 돌려준다"인 이유가 있다. 앞엣것은 본
	 * 시점과 세는 시점 사이에 틈이 있어, 그 틈에 수십 개가 함께 통과하면 한도를 넘는 만큼
	 * 비밀번호를 더 시험해 볼 수 있다. 먼저 세면 그 틈이 없다 — {@code INCR}이 원자적이라
	 * 동시에 들어와도 각자 다른 번호를 받는다.
	 *
	 * <p>0 아래로는 내려가지 않게 한다. 키가 그사이 만료됐는데 빼기만 하면 음수가 남아,
	 * 다음 창에서 한도가 그만큼 늘어난다.
	 */
	public void refund(String bucket) {
		redis.execute(REFUND, List.of(key(bucket)));
	}

	private String key(String bucket) {
		return "ratelimit:" + bucket;
	}

	/**
	 * @param allowed    통과시킬지
	 * @param retryAfter 막혔다면 몇 초 뒤에 다시 오면 되는지
	 */
	public record Decision(boolean allowed, long retryAfter) {
	}
}
