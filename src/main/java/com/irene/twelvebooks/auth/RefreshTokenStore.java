package com.irene.twelvebooks.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
	private static final String CREDENTIAL_VERSION_FIELD = "credVer";

	/**
	 * KEYS[1] 세션 키 · KEYS[2] 역인덱스
	 * ARGV: 1 해시, 2 userId, 3 발급시각, 4 TTL(ms), 5 만료 score, 6 현재 score, 7 자격증명 번호
	 */
	private static final RedisScript<Void> ISSUE_SCRIPT = new DefaultRedisScript<>("""
			redis.call('HSET', KEYS[1], 'userId', ARGV[2], 'issuedAt', ARGV[3], 'credVer', ARGV[7])
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
			local credVer = redis.call('HGET', KEYS[1], 'credVer') or '0'
			redis.call('DEL', KEYS[1])
			local index = 'refresh:user:' .. userId
			redis.call('ZREM', index, ARGV[1])
			local newKey = 'refresh:' .. ARGV[2]
			redis.call('HSET', newKey, 'userId', userId, 'issuedAt', ARGV[3], 'credVer', credVer)
			redis.call('PEXPIRE', newKey, ARGV[4])
			redis.call('ZADD', index, ARGV[5], ARGV[2])
			redis.call('PEXPIRE', index, ARGV[4])
			redis.call('ZREMRANGEBYSCORE', index, 0, ARGV[6])
			return userId
			""", String.class);

	/** KEYS[1] 역인덱스. 그 안의 모든 세션 키와 인덱스 자신을 지운다. */
	private static final RedisScript<Void> REVOKE_ALL_SCRIPT = new DefaultRedisScript<>("""
			local hashes = redis.call('ZRANGE', KEYS[1], 0, -1)
			for _, hash in ipairs(hashes) do
			  redis.call('DEL', 'refresh:' .. hash)
			end
			redis.call('DEL', KEYS[1])
			""", Void.class);

	private final StringRedisTemplate redis;
	private final Duration refreshTokenTtl;
	private final Clock clock;

	public RefreshTokenStore(StringRedisTemplate redis, JwtProperties properties, Clock clock) {
		this.redis = redis;
		this.refreshTokenTtl = properties.refreshTokenTtl();
		this.clock = clock;
	}

	/**
	 * 세션을 만든다.
	 *
	 * @param credentialVersion <b>검증한 해시와 같은 조회에서 온</b> 번호({@code users} 행의
	 *                          {@code credential_version}). 여기서 따로 읽으면 안 된다 —
	 *                          검증과 발급 사이에 비밀번호가 바뀌었을 때 그 세션이 새 번호를
	 *                          달고 살아남는다
	 */
	public String issue(Long userId, String credentialVersion) {
		String rawToken = newToken();
		String hash = hash(rawToken);
		Instant now = clock.instant();

		redis.execute(ISSUE_SCRIPT,
				List.of(sessionKey(hash), userIndexKey(userId)),
				hash, String.valueOf(userId), now.toString(),
				String.valueOf(refreshTokenTtl.toMillis()),
				String.valueOf(now.plus(refreshTokenTtl).toEpochMilli()),
				String.valueOf(now.toEpochMilli()), credentialVersion);

		return rawToken;
	}

	/** 세션이 어느 비밀번호로 만들어졌는지. 세션이 없으면 빈 값이다. */
	public Optional<String> findCredentialVersion(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		Object version = redis.opsForHash().get(sessionKey(hash(rawToken)), CREDENTIAL_VERSION_FIELD);
		return Optional.ofNullable(version).map(Object::toString);
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

	/**
	 * 그 사람의 모든 세션을 끊는다. 비밀번호가 바뀌었을 때 부른다 — 바꾸는 이유가 보통
	 * 탈취이기 때문에, <b>바꾸고도 훔친 기기가 계속 살아 있으면 바꾼 의미가 없다.</b>
	 *
	 * <p>역인덱스를 읽어 세션 키를 하나씩 지우는 일을 스크립트 한 번으로 한다. 나눠 보내면
	 * 그 사이에 재발급이 끼어들어 <b>새로 만들어진 세션만 살아남는</b> 창이 생긴다.
	 */
	public void revokeAll(Long userId) {
		redis.execute(REVOKE_ALL_SCRIPT, List.of(userIndexKey(userId)));
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
		return TokenSecrets.newToken();
	}

	/** 원문이 아니라 해시를 저장한다. 왜인지는 {@link TokenSecrets}에 있다. */
	private String hash(String rawToken) {
		return TokenSecrets.hash(rawToken);
	}

	private String sessionKey(String hash) {
		return SESSION_KEY_PREFIX + hash;
	}

	private String userIndexKey(Long userId) {
		return USER_INDEX_KEY_PREFIX + userId;
	}
}
