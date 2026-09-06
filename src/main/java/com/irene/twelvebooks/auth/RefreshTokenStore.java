package com.irene.twelvebooks.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Refresh 세션 저장소. 토큰은 서명하지 않은 불투명 문자열이고, 유효성의 진실 공급원은 Redis 하나다.
 *
 * <p>키는 사용자가 아니라 <em>토큰</em> 단위다({@code refresh:{해시}}). 사용자당 하나였다면
 * 폰에서 로그인하는 순간 노트북 세션이 끊긴다.
 */
@Component
public class RefreshTokenStore {

	private static final String SESSION_KEY_PREFIX = "refresh:";
	private static final String USER_INDEX_KEY_PREFIX = "refresh:user:";
	private static final String USER_ID_FIELD = "userId";
	private static final String ISSUED_AT_FIELD = "issuedAt";

	/** 128비트로도 충분하지만, 재발급이 잦은 값이라 여유를 둔다. */
	private static final int TOKEN_BYTES = 32;

	private final StringRedisTemplate redis;
	private final Duration refreshTokenTtl;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public RefreshTokenStore(StringRedisTemplate redis, JwtProperties properties, Clock clock) {
		this.redis = redis;
		this.refreshTokenTtl = properties.refreshTokenTtl();
		this.clock = clock;
	}

	public String issue(Long userId) {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		String hash = hash(rawToken);
		redis.opsForHash().putAll(sessionKey(hash), java.util.Map.of(
				USER_ID_FIELD, String.valueOf(userId),
				ISSUED_AT_FIELD, clock.instant().toString()));
		redis.expire(sessionKey(hash), refreshTokenTtl);

		// 전체 로그아웃이 필요해질 때를 위한 역인덱스. 지금은 엔드포인트를 만들지 않는다.
		redis.opsForSet().add(userIndexKey(userId), hash);
		redis.expire(userIndexKey(userId), refreshTokenTtl);

		return rawToken;
	}

	public Optional<Long> findUserId(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		Object userId = redis.opsForHash().get(sessionKey(hash(rawToken)), USER_ID_FIELD);
		return Optional.ofNullable(userId).map(Object::toString).map(Long::valueOf);
	}

	/**
	 * 옛 세션을 지우고 새 토큰을 발급한다. 훔친 토큰의 수명을 재발급 주기로 잘라내기 위해서다.
	 * 이미 쓴 토큰이면 빈 값을 돌려준다.
	 */
	public Optional<String> rotate(String rawToken) {
		return findUserId(rawToken).map(userId -> {
			revoke(rawToken);
			return issue(userId);
		});
	}

	public void revoke(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		String hash = hash(rawToken);
		findUserId(rawToken).ifPresent(userId -> redis.opsForSet().remove(userIndexKey(userId), hash));
		redis.delete(sessionKey(hash));
	}

	/**
	 * 원문이 아니라 해시를 저장한다. Redis 덤프가 그대로 세션 탈취가 되지 않게 한다.
	 * 토큰은 이미 고엔트로피 난수라 솔트·키 스트레칭이 필요 없다.
	 */
	private String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
		}
	}

	private String sessionKey(String hash) {
		return SESSION_KEY_PREFIX + hash;
	}

	private String userIndexKey(Long userId) {
		return USER_INDEX_KEY_PREFIX + userId;
	}
}
