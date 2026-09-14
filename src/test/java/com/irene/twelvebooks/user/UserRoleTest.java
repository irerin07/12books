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
 * 권한은 프로필 수정이 건드릴 수 있는 값이 아니다.
 *
 * <p>프로필 수정은 dirty checking으로 <b>전체 UPDATE</b>를 날린다. 그 UPDATE에 역할이 실리면,
 * 수정 요청이 엔티티를 읽은 뒤 운영자가 SQL로 권한을 회수했을 때 <b>회수가 취소된다</b> —
 * 옛 값이 그대로 다시 저장되기 때문이다.
 *
 * <p>프로필 필드끼리의 갱신 유실(last-write-wins)은 감수하기로 한 것과 다른 문제다. 그쪽은
 * 같은 사람이 자기 이름을 두 번 고치는 드문 상황이고, 이쪽은 <b>권한을 뺏는 조치가 무효가
 * 되는</b> 일이다. 뺏는 이유를 생각하면 그 시차가 가장 곤란하다.
 */
class UserRoleTest extends AbstractIntegrationTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	EntityManager entityManager;

	@Test
	@Transactional
	@DisplayName("프로필 수정은 그사이 회수된 권한을 되살리지 않는다")
	void profileUpdateNeverRewritesRole() {
		Long userId = userRepository.save(
				User.create("admin@example.com", "hash", "admin", "운영자")).getId();
		jdbcTemplate.update("update users set role = 'ADMIN' where id = ?", userId);
		entityManager.clear();

		// 수정 요청이 ADMIN인 상태를 읽는다.
		User loaded = userRepository.findById(userId).orElseThrow();
		assertThat(loaded.getRole()).isEqualTo(UserRole.ADMIN);

		// 그 사이 운영자가 권한을 회수한다.
		jdbcTemplate.update("update users set role = 'USER' where id = ?", userId);

		loaded.updateProfile("새 이름", null, null);
		entityManager.flush();

		// 프로필은 바뀌고 권한은 회수된 채로 남아야 한다.
		assertThat(jdbcTemplate.queryForObject(
				"select role from users where id = ?", String.class, userId)).isEqualTo("USER");
		assertThat(jdbcTemplate.queryForObject(
				"select display_name from users where id = ?", String.class, userId))
				.isEqualTo("새 이름");
	}
}
