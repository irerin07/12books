package com.irene.twelvebooks.user;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로필 수정이 <b>자기 것이 아닌 컬럼</b>을 되돌리지 않는다.
 *
 * <p>dirty checking은 바뀐 필드만 보는 것이 아니라 <b>엔티티 전체</b>를 UPDATE한다. 그래서
 * 프로필 수정이 사용자를 읽은 뒤 비밀번호가 재설정되면, 뒤늦게 나가는 UPDATE가 옛 해시를
 * 다시 저장한다 — <b>재설정이 무효가 된다.</b> 재설정하는 이유가 보통 탈취라는 것을 생각하면
 * 조용히 위험한 자리다.
 *
 * <p>권한(`role`)에서 이미 한 번 겪은 구조다. 다만 비밀번호는 응용이 바꿔야 하므로
 * {@code updatable = false}로 막을 수 없고, <b>바뀐 컬럼만 UPDATE</b>하게 해야 한다.
 */
class UserPasswordUpdateTest extends AbstractIntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	EntityManager entityManager;

	@Test
	@Transactional
	@DisplayName("프로필 수정은 그사이 바뀐 비밀번호를 되돌리지 않는다")
	void profileUpdateNeverRewritesPassword() {
		Long userId = userRepository.save(
				User.create("me@example.com", "옛해시", "irene", "아이린")).getId();
		entityManager.flush();
		entityManager.clear();

		// 프로필 수정 요청이 사용자를 읽는다 — 이 시점의 스냅샷에는 옛 해시가 들어 있다.
		User loaded = userRepository.findById(userId).orElseThrow();

		// 그 사이 비밀번호 재설정이 끝난다.
		jdbcTemplate.update("update users set password_hash = ? where id = ?", "새해시", userId);

		loaded.updateProfile("새 이름", null, null);
		entityManager.flush();

		assertThat(jdbcTemplate.queryForObject(
				"select password_hash from users where id = ?", String.class, userId))
				.isEqualTo("새해시");
		assertThat(jdbcTemplate.queryForObject(
				"select display_name from users where id = ?", String.class, userId))
				.isEqualTo("새 이름");
	}
}
