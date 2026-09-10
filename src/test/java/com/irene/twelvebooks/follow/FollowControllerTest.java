package com.irene.twelvebooks.follow;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FollowControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	SessionFactory sessionFactory;

	private String bearer;
	private String otherBearer;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		userRepository.save(User.create("third@example.com", "hash", "third", "제삼자"));

		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		User other = userRepository.findByHandle("other").orElseThrow();
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());
	}

	private void follow(String bearerToken, String handle) throws Exception {
		mockMvc.perform(post("/api/v1/users/" + handle + "/follow").header("Authorization", bearerToken))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("팔로우하면 상대의 팔로워 목록과 내 팔로잉 목록에 나타난다")
	void follows() throws Exception {
		follow(bearer, "other");

		mockMvc.perform(get("/api/v1/users/other/followers").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].handle").value("irene"))
				.andExpect(jsonPath("$.items[0].displayName").value("아이린"));

		mockMvc.perform(get("/api/v1/users/irene/followings").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].handle").value("other"));
	}

	@Test
	@DisplayName("언팔로우하면 관계가 사라지고, 두 번 해도 실패하지 않는다")
	void unfollows() throws Exception {
		follow(bearer, "other");

		mockMvc.perform(delete("/api/v1/users/other/follow").header("Authorization", bearer))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/users/other/follow").header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/users/other/followers").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	@DisplayName("언팔로우는 관계를 읽지 않고 지운다")
	void unfollowDeletesWithoutReading() throws Exception {
		follow(bearer, "other");

		Statistics statistics = sessionFactory.getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		mockMvc.perform(delete("/api/v1/users/other/follow").header("Authorization", bearer))
				.andExpect(status().isNoContent());

		// 읽고 나서 지우면 두 요청이 같은 행을 함께 읽은 뒤 차례로 지우게 되고, 뒤엣것의 delete가
		// 0행을 만나 Hibernate가 예외를 던진다 — "두 번 눌러도 성공"이 동시 요청에서 깨진다.
		// 한 문장으로 지우면 0행은 그냥 0행이다.
		assertThat(statistics.getEntityStatistics(Follow.class.getName()).getLoadCount()).isZero();
	}

	@Test
	@DisplayName("자기 자신은 팔로우할 수 없다")
	void rejectsSelfFollow() throws Exception {
		mockMvc.perform(post("/api/v1/users/irene/follow").header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("F001"));
	}

	@Test
	@DisplayName("같은 사람을 두 번 팔로우할 수 없다")
	void rejectsDuplicateFollow() throws Exception {
		follow(bearer, "other");

		mockMvc.perform(post("/api/v1/users/other/follow").header("Authorization", bearer))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("F002"));
	}

	@Test
	@DisplayName("없는 사람은 팔로우할 수 없다")
	void rejectsUnknownUser() throws Exception {
		mockMvc.perform(post("/api/v1/users/nobody/follow").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("U003"));
	}

	@Test
	@DisplayName("프로필에 팔로워·팔로잉 수가 함께 나온다")
	void carriesCounts() throws Exception {
		follow(bearer, "other");
		follow(otherBearer, "third");

		mockMvc.perform(get("/api/v1/users/other").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.handle").value("other"))
				.andExpect(jsonPath("$.followerCount").value(1))
				.andExpect(jsonPath("$.followingCount").value(1));

		mockMvc.perform(get("/api/v1/users/irene").header("Authorization", bearer))
				.andExpect(jsonPath("$.followerCount").value(0))
				.andExpect(jsonPath("$.followingCount").value(1));
	}

	@Test
	@DisplayName("목록은 최근에 팔로우한 순서로 나오고, 커서로 나뉘어도 중복도 누락도 없다")
	void pagesListsNewestFirst() throws Exception {
		for (int i = 1; i <= 5; i++) {
			User fan = userRepository.save(
					User.create("fan%d@example.com".formatted(i), "hash", "fan%d".formatted(i), "팬" + i));
			follow("Bearer " + jwtProvider.createAccessToken(fan.getId(), fan.getHandle()), "irene");
		}

		String first = mockMvc.perform(get("/api/v1/users/irene/followers")
						.param("size", "2").header("Authorization", bearer))
				.andExpect(jsonPath("$.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();
		Long cursor = com.jayway.jsonpath.JsonPath.parse(first).read("$.nextCursor", Long.class);

		String second = mockMvc.perform(get("/api/v1/users/irene/followers")
						.param("size", "2").param("cursor", String.valueOf(cursor))
						.header("Authorization", bearer))
				.andReturn().getResponse().getContentAsString();

		List<String> page1 = com.jayway.jsonpath.JsonPath.parse(first).read("$.items[*].handle");
		List<String> page2 = com.jayway.jsonpath.JsonPath.parse(second).read("$.items[*].handle");
		// 마지막에 팔로우한 fan5가 맨 위다 — 커서가 관계의 id라 얻어지는 순서다.
		assertThat(page1).containsExactly("fan5", "fan4");
		assertThat(page2).containsExactly("fan3", "fan2");
	}

	@Test
	@DisplayName("없는 사람의 목록은 404")
	void rejectsUnknownUserList() throws Exception {
		mockMvc.perform(get("/api/v1/users/nobody/followers").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("U003"));
	}

	@Test
	@DisplayName("토큰 없이는 팔로우할 수 없다")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/users/other/follow"))
				.andExpect(status().isUnauthorized());
	}
}
