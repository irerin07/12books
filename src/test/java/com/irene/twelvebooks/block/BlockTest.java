package com.irene.twelvebooks.block;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 차단 관계 자체. 무엇이 보이고 안 보이는지는 {@code BlockVisibilityTest}가 지킨다.
 *
 * <p>팔로우와 같은 모양으로 둔다 — {@code POST}/{@code DELETE}가 모두 204다. 관계는
 * "있다/없다"가 전부이고 돌려줄 표현이 따로 없다.
 */
class BlockTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
	}

	@Test
	@DisplayName("차단하고 풀 수 있다")
	void blocksAndUnblocks() throws Exception {
		mockMvc.perform(post("/api/v1/users/{handle}/block", "other").header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/me/blocks").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].handle").value("other"))
				.andExpect(jsonPath("$.items[0].displayName").value("남"));

		mockMvc.perform(delete("/api/v1/users/{handle}/block", "other").header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/me/blocks").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	/**
	 * 중복은 유니크 제약이 1차 방어선이다 — 먼저 조회해서 있으면 넘어가는 식이면 동시에 들어온
	 * 두 요청이 함께 "없음"을 보고 둘 다 넣는다(`CLAUDE.md` 코드 규약).
	 */
	@Test
	@DisplayName("같은 사람을 두 번 차단하면 409다")
	void rejectsDuplicateBlock() throws Exception {
		mockMvc.perform(post("/api/v1/users/{handle}/block", "other").header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/users/{handle}/block", "other").header("Authorization", bearer))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("B002"));
	}

	@Test
	@DisplayName("자기 자신은 차단할 수 없다")
	void rejectsSelfBlock() throws Exception {
		mockMvc.perform(post("/api/v1/users/{handle}/block", "irene").header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("B001"));
	}

	@Test
	@DisplayName("없는 사람은 차단할 수 없다")
	void rejectsUnknownTarget() throws Exception {
		mockMvc.perform(post("/api/v1/users/{handle}/block", "nobody").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("U003"));
	}

	/** 풀 때는 없어도 204다. 결과가 같으므로("차단돼 있지 않다") 오류로 만들 이유가 없다. */
	@Test
	@DisplayName("차단하지 않은 사람을 풀어도 204다")
	void unblockIsIdempotent() throws Exception {
		mockMvc.perform(delete("/api/v1/users/{handle}/block", "other").header("Authorization", bearer))
				.andExpect(status().isNoContent());
	}
}
