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

		// TTL이 음수로 오는 짧은 틈이 있다(막 만료됐거나 아직 안 걸렸을 때).
		// 그대로 Retry-After에 실으면 클라이언트가 해석할 수 없는 값이 된다.
		long retryAfter = ttl > 0 ? ttl : window.toSeconds();
		return new Decision(count <= limit, retryAfter);
	}

	/**
	 * <b>세지 않고</b> 현재 상태만 본다. 실패만 세는 경로가 요청을 받아들일지 판단할 때 쓴다 —
	 * 여기서 올리면 성공한 요청까지 한도를 깎는다.
	 */
	public Decision peek(String bucket, int limit, Duration window) {
		String raw = redis.opsForValue().get(key(bucket));
		long count = raw == null ? 0 : Long.parseLong(raw);
		Long ttl = redis.getExpire(key(bucket));
		long retryAfter = ttl != null && ttl > 0 ? ttl : window.toSeconds();
		return new Decision(count < limit, retryAfter);
	}

	/** 한 번 올린다. 결과를 보고 나서 세는 경로가 쓴다. */
	public void record(String bucket, Duration window) {
		redis.execute(COUNT_AND_EXPIRE, List.of(key(bucket)), String.valueOf(window.toSeconds()));
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
