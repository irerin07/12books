package com.irene.twelvebooks.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Refresh 세션 저장소. 토큰은 서명하지 않은 불투명 문자열이고, 유효성의 진실 공급원은 Redis 하나다.
 *
 * <p>키는 사용자가 아니라 <em>토큰</em> 단위다({@code refresh:{해시}}). 사용자당 하나였다면
 * 폰에서 로그인하는 순간 노트북 세션이 끊긴다.
 *
 * <p><strong>Redis Cluster에서는 쓸 수 없다.</strong> 스크립트가 세션 키와 역인덱스를 함께 만지는데
 * 둘의 hash slot이 다르면 CROSSSLOT 오류가 난다. 사용자를 모른 채 토큰만으로 세션을 찾아야 해서
 * hash tag로 슬롯을 묶을 수도 없다 — 클러스터가 필요해지면 키 설계부터 다시 해야 한다.
 * standalone 또는 replica 구성을 전제로 한다.
 *
 * <p>발급과 교체는 각각 Lua 스크립트 한 번으로 끝낸다. Redis는 스크립트를 원자적으로 실행하므로
 * 같은 토큰으로 동시에 재발급을 시도해도 <strong>하나만</strong> 성공한다. 명령을 나눠 보내면
 * 두 요청이 모두 검증을 통과해 각자 새 토큰을 받아 갈 수 있고, HSET과 EXPIRE 사이에서
 * 프로세스가 죽으면 만료되지 않는 세션이 남는다.
 */
@Component
public class RefreshTokenStore {

	private static final String SESSION_KEY_PREFIX = "refresh:";
	private static final String USER_INDEX_KEY_PREFIX = "refresh:user:";
	private static final String USER_ID_FIELD = "userId";
	private static final String ISSUED_AT_FIELD = "issuedAt";

	/** 128비트로도 충분하지만, 재발급이 잦은 값이라 여유를 둔다. */
	private static final int TOKEN_BYTES = 32;

	/**
	 * KEYS[1] 세션 키 · KEYS[2] 역인덱스
	 * ARGV: 1 해시, 2 userId, 3 발급시각, 4 TTL(ms), 5 만료 score, 6 현재 score
	 */
	private static final RedisScript<Void> ISSUE_SCRIPT = new DefaultRedisScript<>("""
			redis.call('HSET', KEYS[1], 'userId', ARGV[2], 'issuedAt', ARGV[3])
			redis.call('PEXPIRE', KEYS[1], ARGV[4])
			redis.call('ZADD', KEYS[2], ARGV[5], ARGV[1])
			redis.call('PEXPIRE', KEYS[2], ARGV[4])
			redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, ARGV[6])
			""", Void.class);

	/**
	 * KEYS[1] 옛 세션 키
	 * ARGV: 1 옛 해시, 2 새 해시, 3 발급시각, 4 TTL(ms), 5 만료 score, 6 현재 score
	 * 성공하면 userId를, 이미 쓰였거나 없으면 nil을 돌려준다.
	 */
	private static final RedisScript<String> ROTATE_SCRIPT = new DefaultRedisScript<>("""
			local userId = redis.call('HGET', KEYS[1], 'userId')
			if not userId then return nil end
			redis.call('DEL', KEYS[1])
			local index = 'refresh:user:' .. userId
			redis.call('ZREM', index, ARGV[1])
			local newKey = 'refresh:' .. ARGV[2]
			redis.call('HSET', newKey, 'userId', userId, 'issuedAt', ARGV[3])
			redis.call('PEXPIRE', newKey, ARGV[4])
			redis.call('ZADD', index, ARGV[5], ARGV[2])
			redis.call('PEXPIRE', index, ARGV[4])
			redis.call('ZREMRANGEBYSCORE', index, 0, ARGV[6])
			return userId
			""", String.class);

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
		String rawToken = newToken();
		String hash = hash(rawToken);
		Instant now = clock.instant();

		redis.execute(ISSUE_SCRIPT,
				List.of(sessionKey(hash), userIndexKey(userId)),
				hash, String.valueOf(userId), now.toString(),
				String.valueOf(refreshTokenTtl.toMillis()),
				String.valueOf(now.plus(refreshTokenTtl).toEpochMilli()),
				String.valueOf(now.toEpochMilli()));

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
	 * 옛 세션을 지우고 새 토큰을 발급한다. 훔친 토큰이 살아 있는 창을 재발급 주기로 잘라낸다.
	 *
	 * <p>스크립트가 옛 키의 존재를 확인하고 지우는 것까지 한 번에 하므로, 호출부가
	 * {@link #findUserId}로 미리 검증한 뒤 다른 요청이 먼저 교체했더라도 여기서 빈 값이 나온다.
	 * 즉 이 메서드가 사실상 compare-and-swap 역할을 한다.
	 *
	 * <p>교체에 성공한 뒤 HTTP 응답이 유실되면 클라이언트는 새 토큰을 받지 못한 채
	 * 죽은 옛 토큰만 갖게 된다. 엄격한 rotation의 일반적인 한계이고, 이 서비스는
	 * 그 경우 다시 로그인하는 것을 받아들인다 — 재사용 감지를 포기하는 것보다 낫다고 본다.
	 */
	public Optional<String> rotate(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		String oldHash = hash(rawToken);
		String newToken = newToken();
		String newHash = hash(newToken);
		Instant now = clock.instant();

		String userId = redis.execute(ROTATE_SCRIPT,
				List.of(sessionKey(oldHash)),
				oldHash, newHash, now.toString(),
				String.valueOf(refreshTokenTtl.toMillis()),
				String.valueOf(now.plus(refreshTokenTtl).toEpochMilli()),
				String.valueOf(now.toEpochMilli()));

		return Optional.ofNullable(userId).map(id -> newToken);
	}

	public void revoke(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		String hash = hash(rawToken);
		findUserId(rawToken).ifPresent(userId -> redis.opsForZSet().remove(userIndexKey(userId), hash));
		redis.delete(sessionKey(hash));
	}

	private String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
