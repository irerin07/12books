package com.irene.twelvebooks.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 비밀번호가 몇 번 바뀌었는가. 세션이 <b>어느 비밀번호로</b> 만들어졌는지를 가리는 표식이다.
 *
 * <p>세션을 전부 끊는 것만으로는 부족하다. 옛 비밀번호로 이미 검증을 통과한 로그인이
 * 무효화가 끝난 <b>뒤에</b> 세션을 만들면 그 세션은 끊긴 적이 없어 계속 재발급된다 —
 * 비밀번호를 바꿨는데 옛 비밀번호를 아는 사람이 그대로 남는 것이라, 바꾼 의미가 사라진다.
 *
 * <p>그래서 로그인은 <b>비밀번호를 확인한 시점의 번호</b>를 세션에 적어 두고, 재발급은 그것이
 * 지금 번호와 같은지 본다. 늦게 만들어진 세션은 옛 번호를 들고 있어 첫 재발급에서 걸린다.
 *
 * <p>Redis에 둔 이유는 세션이 거기 있기 때문이다. DB에 컬럼을 더하면 재발급마다 DB를 한 번
 * 더 읽게 되고, Redis가 비면 세션도 함께 사라지므로 둘의 수명이 어긋날 일도 없다.
 */
@Component
public class CredentialVersions {

	/** 한 번도 바꾼 적 없는 상태. 키가 없으면 이 값이다. */
	public static final String INITIAL = "0";

	private static final String KEY_PREFIX = "credver:";

	private final StringRedisTemplate redis;
	private final JwtProperties properties;

	public CredentialVersions(StringRedisTemplate redis, JwtProperties properties) {
		this.redis = redis;
		this.properties = properties;
	}

	public String current(Long userId) {
		String version = redis.opsForValue().get(key(userId));
		return version == null ? INITIAL : version;
	}

	/**
	 * 번호를 올린다. 비밀번호가 바뀔 때 부른다.
	 *
	 * <p>수명을 refresh 토큰과 맞춘다. 그보다 오래 둘 이유가 없다 — 그 시점이면 옛 번호를
	 * 든 세션은 어차피 전부 만료돼 있다.
	 */
	public void bump(Long userId) {
		redis.opsForValue().increment(key(userId));
		redis.expire(key(userId), properties.refreshTokenTtl());
	}

	private String key(Long userId) {
		return KEY_PREFIX + userId;
	}
}
