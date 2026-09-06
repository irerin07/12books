package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenStoreTest extends AbstractIntegrationTest {

	@Autowired
	RefreshTokenStore store;

	@Autowired
	StringRedisTemplate redis;

	@BeforeEach
	void clearRedis() {
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	private Set<String> sessionKeys() {
		Set<String> keys = redis.keys("refresh:*");
		keys.removeIf(key -> key.startsWith("refresh:user:"));
		return keys;
	}

	@Test
	@DisplayName("발급한 refresh 토큰으로 사용자를 찾는다")
	void issuesAndResolvesToken() {
		String token = store.issue(42L);

		assertThat(store.findUserId(token)).contains(42L);
	}

	@Test
	@DisplayName("Redis에는 원문이 아니라 해시가 저장된다")
	void storesHashNotRawToken() {
		String token = store.issue(42L);

		assertThat(redis.hasKey("refresh:" + token)).isFalse();
		assertThat(sessionKeys()).hasSize(1).allSatisfy(key -> assertThat(key).doesNotContain(token));
	}

	@Test
	@DisplayName("세션은 refresh TTL만큼 살아 있다")
	void sessionExpiresWithRefreshTtl() {
		store.issue(42L);

		String key = sessionKeys().iterator().next();

		assertThat(redis.getExpire(key, TimeUnit.SECONDS))
				.isBetween(Duration.ofDays(14).toSeconds() - 60, Duration.ofDays(14).toSeconds());
	}

	@Test
	@DisplayName("같은 사용자가 여러 기기에서 로그인해도 세션이 함께 살아 있다")
	void keepsSessionPerDevice() {
		String phone = store.issue(42L);
		String laptop = store.issue(42L);

		assertThat(phone).isNotEqualTo(laptop);
		assertThat(store.findUserId(phone)).contains(42L);
		assertThat(store.findUserId(laptop)).contains(42L);
	}

	@Test
	@DisplayName("rotation 하면 새 토큰만 유효하고 옛 토큰은 죽는다")
	void rotationInvalidatesPreviousToken() {
		String old = store.issue(42L);

		String rotated = store.rotate(old).orElseThrow();

		assertThat(rotated).isNotEqualTo(old);
		assertThat(store.findUserId(rotated)).contains(42L);
		assertThat(store.findUserId(old)).isEmpty();
	}

	@Test
	@DisplayName("이미 쓴 토큰으로 다시 rotation 할 수 없다")
	void cannotRotateConsumedToken() {
		String old = store.issue(42L);
		store.rotate(old);

		assertThat(store.rotate(old)).isEmpty();
	}

	@Test
	@DisplayName("로그아웃은 그 세션만 지우고 다른 기기는 건드리지 않는다")
	void revokeRemovesOnlyThatSession() {
		String phone = store.issue(42L);
		String laptop = store.issue(42L);

		store.revoke(phone);

		assertThat(store.findUserId(phone)).isEmpty();
		assertThat(store.findUserId(laptop)).contains(42L);
	}

	@Test
	@DisplayName("모르는 토큰은 조용히 거부한다")
	void rejectsUnknownToken() {
		assertThat(store.findUserId("아무거나")).isEmpty();
		assertThat(store.findUserId(null)).isEmpty();
		assertThat(store.rotate("아무거나")).isEmpty();
	}

	@Test
	@DisplayName("역인덱스가 사용자의 세션 해시를 모아둔다 (전체 로그아웃 대비)")
	void maintainsPerUserIndex() {
		store.issue(42L);
		String laptop = store.issue(42L);

		assertThat(redis.opsForSet().size("refresh:user:42")).isEqualTo(2);

		store.revoke(laptop);

		assertThat(redis.opsForSet().size("refresh:user:42")).isEqualTo(1);
	}
}
