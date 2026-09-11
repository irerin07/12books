package com.irene.twelvebooks.e2e;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5의 핵심 여정. 탐색 피드에서 만난 사람을 팔로우하면 그 사람의 글이 내 타임라인으로
 * 흘러들어오고, 언팔로우하면 빠진다.
 *
 * <p>여기서 제품이 SNS가 된다 — 그전까지 피드는 모두에게 같은 화면이었다.
 */
class FollowJourneyE2ETest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;
	private String friendBearer;
	private String strangerBearer;
	private Long bookId;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User friend = userRepository.save(User.create("friend@example.com", "hash", "friend", "친구"));
		User stranger = userRepository.save(User.create("stranger@example.com", "hash", "stranger", "남"));

		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		friendBearer = "Bearer " + jwtProvider.createAccessToken(friend.getId(), friend.getHandle());
		strangerBearer = "Bearer " + jwtProvider.createAccessToken(stranger.getId(), stranger.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", "https://example.com/c.jpg", null)).getId();
	}

	private void write(String bearerToken, String content) throws Exception {
		mockMvc.perform(post("/api/v1/posts").header("Authorization", bearerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"content":"%s"}""".formatted(bookId, content)))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("탐색 피드에서 만난 사람을 팔로우하면 타임라인이 생기고, 언팔로우하면 되돌아간다")
	void discoverFollowAndUnfollow() throws Exception {
		write(friendBearer, "친구의 감상");
		write(strangerBearer, "남의 감상");
		write(bearer, "내 감상");

		// 1. 팔로우 전 — 탐색 피드에는 셋 다 있지만 내 타임라인은 비어 있다.
		//    홈이 빈 것이 곧 "팔로우할 이유"이고, 내 글은 내 글대로 따로 본다.
		mockMvc.perform(get("/api/v1/feed/explore").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(3));
		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/users/irene/posts").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].content").value("내 감상"));

		// 2. 탐색 피드에서 본 사람을 팔로우한다
		mockMvc.perform(post("/api/v1/users/friend/follow").header("Authorization", bearer))
				.andExpect(status().isNoContent());

		// 3. 타임라인에 그 사람의 글이 흘러들어온다 — 남의 글도 내 글도 섞이지 않는다
		String feed = mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.parse(feed).<List<String>>read("$.items[*].content"))
				.containsExactly("친구의 감상");

		// 4. 관계가 양쪽 프로필과 목록에 드러난다
		mockMvc.perform(get("/api/v1/users/friend").header("Authorization", bearer))
				.andExpect(jsonPath("$.followerCount").value(1))
				.andExpect(jsonPath("$.followingCount").value(0));
		mockMvc.perform(get("/api/v1/users/friend/followers").header("Authorization", bearer))
				.andExpect(jsonPath("$.items[0].handle").value("irene"));
		mockMvc.perform(get("/api/v1/users/irene/followings").header("Authorization", bearer))
				.andExpect(jsonPath("$.items[0].displayName").value("친구"));

		// 5. 자기 자신 팔로우 400, 중복 팔로우 409
		mockMvc.perform(post("/api/v1/users/irene/follow").header("Authorization", bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("F001"));
		mockMvc.perform(post("/api/v1/users/friend/follow").header("Authorization", bearer))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("F002"));

		// 6. 언팔로우하면 그 사람의 글이 빠져 타임라인이 다시 비고,
		//    글 자체는 탐색 피드에 그대로 남는다
		mockMvc.perform(delete("/api/v1/users/friend/follow").header("Authorization", bearer))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/feed/explore").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(3));
		mockMvc.perform(get("/api/v1/users/friend").header("Authorization", bearer))
				.andExpect(jsonPath("$.followerCount").value(0));
	}
}
