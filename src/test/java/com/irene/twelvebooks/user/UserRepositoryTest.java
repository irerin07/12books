package com.irene.twelvebooks.user;

import com.irene.twelvebooks.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void clean() {
		// 컨테이너를 전체 테스트가 공유하므로 다른 클래스가 남긴 행이 유니크 제약에 걸린다.
		userRepository.deleteAll();
	}

	private User newUser(String email, String handle) {
		return User.create(email, "{bcrypt}해시된값", handle, "아이린");
	}

	@Test
	@DisplayName("저장한 사용자를 email로 찾는다")
	void findsByEmail() {
		userRepository.save(newUser("irene@example.com", "irene"));

		assertThat(userRepository.findByEmail("irene@example.com"))
				.get()
				.extracting(User::getHandle)
				.isEqualTo("irene");
	}

	@Test
	@DisplayName("저장한 사용자를 handle로 찾는다")
	void findsByHandle() {
		userRepository.save(newUser("irene2@example.com", "irene2"));

		assertThat(userRepository.findByHandle("irene2")).isPresent();
	}

	@Test
	@DisplayName("저장 시 createdAt·updatedAt이 자동으로 채워진다")
	void auditingFillsTimestamps() {
		User saved = userRepository.save(newUser("irene3@example.com", "irene3"));

		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}

	@Test
	@DisplayName("email 중복은 DB 유니크 제약이 막는다")
	void rejectsDuplicateEmail() {
		userRepository.saveAndFlush(newUser("dup@example.com", "dup1"));

		assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("dup@example.com", "dup2")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("handle 중복은 DB 유니크 제약이 막는다")
	void rejectsDuplicateHandle() {
		userRepository.saveAndFlush(newUser("h1@example.com", "samehandle"));

		assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("h2@example.com", "samehandle")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
