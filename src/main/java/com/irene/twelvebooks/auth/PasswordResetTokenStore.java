package com.irene.twelvebooks.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 재설정 토큰 저장소. refresh 세션과 같은 자리(Redis)를 쓰지만 성질이 다르다 —
 * <b>한 번 쓰면 사라지고</b>, 수명이 짧다.
 *
 * <p>DB에 두지 않는 이유는 만료가 공짜이기 때문이다. 테이블에 두면 지나간 토큰을 지우는 일이
 * 따로 생기고, 그 청소를 잊으면 만료된 줄 알았던 링크가 살아 있다.
 */
@Component
public class PasswordResetTokenStore {

	private static final String KEY_PREFIX = "pwreset:";

	/**
	 * 읽고 <b>동시에</b> 지운다.
	 *
	 * <p>나눠 부르면 같은 링크를 두 번 누른 두 요청이 모두 값을 읽고 둘 다 통과한다. 1회용이
	 * 1회용이 아니게 되는 것이라, 링크가 유출됐을 때 정확히 무너지는 자리다.
	 */
	private static final RedisScript<String> CONSUME = new DefaultRedisScript<>("""
			local userId = redis.call('GET', KEYS[1])
			if userId then
			  redis.call('DEL', KEYS[1])
			end
			return userId
			""", String.class);

	private final StringRedisTemplate redis;
	private final PasswordResetProperties properties;

	public PasswordResetTokenStore(StringRedisTemplate redis, PasswordResetProperties properties) {
		this.redis = redis;
		this.properties = properties;
	}

	/** 토큰 원문을 돌려준다. 서버에는 해시만 남으므로 이 값은 지금 이 자리에서만 존재한다. */
	public String issue(Long userId) {
		String rawToken = TokenSecrets.newToken();
		redis.opsForValue().set(key(rawToken), String.valueOf(userId), properties.ttl());
		return rawToken;
	}

	/**
	 * 토큰을 쓴다. 성공하면 그 토큰은 그 순간 없어진다.
	 *
	 * @return 토큰의 주인. 없거나·만료됐거나·이미 쓰인 토큰이면 빈 값 — <b>셋을 구분하지
	 *         않는다.</b> "만료됐다"와 "그런 토큰은 없다"를 나눠 답하면, 남의 토큰을 찔러 보며
	 *         존재 여부를 확인할 수 있다.
	 */
	public Optional<Long> consume(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		String userId = redis.execute(CONSUME, List.of(key(rawToken)));
		return Optional.ofNullable(userId).map(Long::valueOf);
	}

	private String key(String rawToken) {
		return KEY_PREFIX + TokenSecrets.hash(rawToken);
	}
}
