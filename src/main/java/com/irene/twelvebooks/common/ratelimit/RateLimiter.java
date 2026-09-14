package com.irene.twelvebooks.common.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * 고정 창 카운터. 창이 시작될 때 1부터 세고, 창이 끝나면 키가 사라져 처음부터 다시 센다.
 *
 * <p>Redis가 이미 refresh 세션을 들고 있어 <b>새 인프라 없이</b> 된다. 슬라이딩 윈도가 더
 * 정밀하지만 요청마다 기록을 쌓아야 하고, 지금 필요한 것은 "스크립트가 두드리는 것을 막는"
 * 수준이라 고정 창으로 충분하다.
 *
 * <p><b>창마다 다른 키를 쓴다.</b> 키 이름에 창 번호가 들어가므로, 창이 바뀌면 예약과 반납이
 * 서로 다른 키를 본다 — 창이 끝나기 직전에 예약한 요청이 늦게 성공해도 다음 창의 몫을
 * 깎지 못한다.
 */
@Component
public class RateLimiter {

	/**
	 * 한도를 <b>스크립트 안에서</b> 보고, 통과시킬 때만 센다.
	 *
	 * <p>바깥에서 세고 나서 판단하면 두 가지가 어긋난다. 막힌 요청까지 세어 카운터가 부풀고,
	 * 그 값이 예약을 돌려받은 뒤에도 남아 <b>인증 실패가 한 건도 없는데 정상 로그인이 막힌다.</b>
	 * 보고 나서 세는 방식도 안 된다 — 본 시점과 세는 시점 사이에 동시 요청이 함께 빠져나간다.
	 *
	 * <p>만료는 창의 남은 시간으로 한 번만 건다. 세기와 만료를 나눠 부르면 그 사이에 죽었을 때
	 * 만료 없는 키가 남아 그 사람이 영원히 막힌다.
	 */
	private static final RedisScript<List> ADMIT = new DefaultRedisScript<>("""
			local limit = tonumber(ARGV[1])
			local remaining = tonumber(ARGV[2])
			local count = tonumber(redis.call('GET', KEYS[1]) or '0')
			if count >= limit then
			  local ttl = redis.call('TTL', KEYS[1])
			  return { 0, ttl }
			end
			count = redis.call('INCR', KEYS[1])
			if count == 1 then
			  redis.call('EXPIRE', KEYS[1], remaining)
			end
			return { 1, redis.call('TTL', KEYS[1]) }
			""", List.class);

	/** 돌려주되 0 아래로는 내려가지 않는다. 키가 이미 사라졌으면 아무것도 하지 않는다. */
	private static final RedisScript<Long> REFUND = new DefaultRedisScript<>("""
			local current = tonumber(redis.call('GET', KEYS[1]) or '0')
			if current > 0 then
			  return redis.call('DECR', KEYS[1])
			end
			return 0
			""", Long.class);

	private final StringRedisTemplate redis;
	private final Clock clock;

	public RateLimiter(StringRedisTemplate redis, Clock clock) {
		this.redis = redis;
		this.clock = clock;
	}

	/**
	 * 한 자리를 잡아 본다.
	 *
	 * @param bucket 버킷 이름과 대상을 합친 것. 엔드포인트마다 달라야 한다
	 * @param limit  한 창 안에 허용할 횟수
	 * @param window 창의 길이
	 */
	public Decision check(String bucket, int limit, Duration window) {
		long now = clock.instant().getEpochSecond();
		long seconds = window.toSeconds();
		long windowIndex = now / seconds;
		String key = key(bucket, windowIndex);
		long remaining = (windowIndex + 1) * seconds - now;

		List<?> result = redis.execute(ADMIT, List.of(key),
				String.valueOf(limit), String.valueOf(remaining));
		boolean admitted = ((Number) result.get(0)).longValue() == 1;
		long ttl = ((Number) result.get(1)).longValue();

		return new Decision(admitted, retryAfterFrom(ttl, window), key);
	}

	/**
	 * 잡아 둔 자리를 돌려준다. 실패만 세는 경로가 <b>성공했을 때</b> 부른다.
	 *
	 * <p>"보고 나서 센다"가 아니라 "먼저 잡고 성공하면 돌려준다"인 이유가 있다. 앞엣것은 본
	 * 시점과 세는 시점 사이에 틈이 있어, 그 틈에 수십 개가 함께 통과하면 한도를 넘는 만큼
	 * 비밀번호를 더 시험해 볼 수 있다.
	 *
	 * @param key {@link Decision#key()}가 준 그 키. 버킷 이름이 아니라 <b>키</b>를 받는 것이
	 *            중요하다 — 창이 바뀐 뒤에 돌려주면 다음 창의 몫을 깎게 된다
	 */
	public void refund(String key) {
		redis.execute(REFUND, List.of(key));
	}

	/**
	 * 남은 시간을 {@code Retry-After}에 실을 수 있는 값으로 바꾼다.
	 *
	 * <p>{@code 0}은 <b>곧 풀린다</b>는 뜻이지 오류가 아니다. 그것을 창 전체로 바꾸면 1초 뒤면
	 * 되는 사람에게 창 전체를 기다리라고 안내하게 된다. 음수만 이상 신호로 보고 창 길이를 준다 —
	 * {@code -1}은 만료가 안 걸린 키, {@code -2}는 그사이 사라진 키다.
	 */
	static long retryAfterFrom(long ttl, Duration window) {
		if (ttl > 0) {
			return ttl;
		}
		return ttl == 0 ? 1 : window.toSeconds();
	}

	private String key(String bucket, long windowIndex) {
		return "ratelimit:" + bucket + ":" + windowIndex;
	}

	/**
	 * @param allowed    통과시킬지
	 * @param retryAfter 막혔다면 몇 초 뒤에 다시 오면 되는지
	 * @param key        이번에 잡은 자리의 키. 돌려줄 때 그대로 쓴다
	 */
	public record Decision(boolean allowed, long retryAfter, String key) {
	}
}
