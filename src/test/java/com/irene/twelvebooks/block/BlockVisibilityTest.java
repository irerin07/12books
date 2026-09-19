package com.irene.twelvebooks.block;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 차단하면 어디에서도 서로 보이지 않는다(plan.md L2-2 완료 기준).
 *
 * <p><b>양방향이다.</b> 행은 누가 눌렀는지만 기록하지만, 차단한 쪽도 차단당한 쪽도 상대를
 * 보지 못한다. 한쪽만 막으면 차단당한 사람이 계속 따라다닐 수 있다.
 *
 * <p>한 화면이라도 빠뜨리면 "차단했는데 팔로워 목록에선 보인다"가 된다. 그래서 목록을 하나씩
 * 세는 것이 아니라 <b>모든 노출 경로</b>를 한 번에 확인한다. 빠뜨려도 아무도 모르는 종류의
 * 실수라 사람 눈으로 막을 수 없고, 그래서 {@code BlockConventionTest}가 따로 있다.
 */
class BlockVisibilityTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String mine;
	private String theirs;
	private Long bookId;
	private Long theirPostId;
	private Long myPostId;

	@BeforeEach
	void setUp() throws Exception {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User them = userRepository.save(User.create("them@example.com", "hash", "them", "남"));
		mine = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		theirs = "Bearer " + jwtProvider.createAccessToken(them.getId(), them.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		theirPostId = writePost(theirs, "남이 쓴 글이다.");
		myPostId = writePost(mine, "내가 쓴 글이다.");

		// 서로 팔로우한 사이에서 시작한다 — 차단이 관계를 어떻게 다루는지까지 보려면 필요하다.
		follow(mine, "them");
		follow(theirs, "irene");
	}

	@Test
	@DisplayName("차단하면 차단한 쪽에서 상대가 모든 화면에서 사라진다")
	void hidesBlockedFromBlocker() throws Exception {
		block(mine, "them");

		assertCannotSee(mine, theirPostId, "them");
	}

	/**
	 * 차단당한 쪽에서도 사라진다. 행은 한 방향이지만 조회는 두 방향을 본다.
	 *
	 * <p>여기가 빠지면 차단이 "내 눈만 가리는 것"이 되어, 차단당한 사람이 글을 찾아와
	 * 댓글을 달 수 있다.
	 */
	@Test
	@DisplayName("차단당한 쪽에서도 상대가 모든 화면에서 사라진다")
	void hidesBlockerFromBlocked() throws Exception {
		block(mine, "them");

		assertCannotSee(theirs, myPostId, "irene");
	}

	/**
	 * 팔로우는 <b>차단한 쪽만</b> 끊는다. 상대가 나를 팔로우하는 것은 상대의 의사이고,
	 * 차단이 그것을 지울 근거가 아니다. 남은 관계는 조회에서 가려지고, 차단을 풀면 돌아온다.
	 */
	@Test
	@DisplayName("차단하면 내가 건 팔로우만 끊기고, 상대가 건 것은 남는다")
	void dropsOnlyTheBlockersFollow() throws Exception {
		block(mine, "them");

		// 차단을 풀면 상대의 팔로우가 그대로 돌아온다 — 지워지지 않았다는 증거다.
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.delete("/api/v1/users/{handle}/block", "them").header("Authorization", mine))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/users/{handle}/followers", "irene").header("Authorization", mine))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].handle").value("them"));
		// 내가 걸었던 팔로우는 돌아오지 않는다.
		mockMvc.perform(get("/api/v1/users/{handle}/followings", "irene").header("Authorization", mine))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	@DisplayName("차단을 풀면 다시 보인다")
	void restoresVisibilityOnUnblock() throws Exception {
		block(mine, "them");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.delete("/api/v1/users/{handle}/block", "them").header("Authorization", mine))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/posts/{id}", theirPostId).header("Authorization", mine))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/feed").header("Authorization", mine))
				.andExpect(jsonPath("$.items.length()").value(1));
	}

	/** 상대가 보이면 안 되는 모든 경로. 하나라도 새면 차단이 아니다. */
	private void assertCannotSee(String viewer, Long theirPost, String theirHandle) throws Exception {
		// 단건 — 목록에서만 가리면 링크로 그대로 열린다.
		mockMvc.perform(get("/api/v1/posts/{id}", theirPost).header("Authorization", viewer))
				.andExpect(status().isNotFound());
		// 홈(아직 팔로우하지 않은 사람들)
		mockMvc.perform(get("/api/v1/feed").header("Authorization", viewer))
				.andExpect(jsonPath("$.items.length()").value(0));
		// 팔로잉 타임라인
		mockMvc.perform(get("/api/v1/feed/following").header("Authorization", viewer))
				.andExpect(jsonPath("$.items.length()").value(0));
		// 책별 목록 — 둘 다 같은 책에 썼다. 내 글 하나만 남아야 한다.
		mockMvc.perform(get("/api/v1/books/{id}/posts", bookId).header("Authorization", viewer))
				.andExpect(jsonPath("$.items.length()").value(1));
		// 상대의 프로필과 그 아래 목록은 통째로 없는 사람이 된다. 관계 목록 자체는 차단을
		// 거르지 않지만(언팔한 것이 아니므로), 차단한 사람의 프로필은 애초에 열리지 않는다.
		mockMvc.perform(get("/api/v1/users/{handle}", theirHandle).header("Authorization", viewer))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/users/{handle}/posts", theirHandle).header("Authorization", viewer))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/users/{handle}/followers", theirHandle).header("Authorization", viewer))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/users/{handle}/followings", theirHandle).header("Authorization", viewer))
				.andExpect(status().isNotFound());
	}

	/**
	 * <b>차단해도 관계 목록은 그대로다.</b> 내가 누군가를 차단해도 그 사람이 나를 언팔한 것은
	 * 아니다 — 관계는 남아 있고, 목록은 관계를 보여 주는 자리다.
	 *
	 * <p>지우면 "차단했더니 내 팔로워가 줄었다"가 된다. 차단은 내 의사이지 상대의 관계를 끊을
	 * 근거가 아니다. 그래서 팔로워 수도 <b>모두에게 같은 값</b>이다 — 보는 사람마다 다른 수를
	 * 주면 "A의 팔로워 수"가 A의 속성이 아니라 (A, 보는 사람)의 함수가 된다.
	 *
	 * <p>가리는 것은 그 사람의 <b>내용</b>(글·댓글·프로필)이지 관계가 아니다. 목록에서 이름을
	 * 눌러도 프로필은 404다.
	 */
	@Test
	@DisplayName("차단해도 관계 목록과 팔로워 수는 그대로다 — 언팔한 것이 아니다")
	void keepsRelationListsIntact() throws Exception {
		User third = userRepository.save(User.create("third@example.com", "hash", "third", "제삼자"));
		follow(mine, "third");
		follow(theirs, "third");

		block(mine, "them");

		// 제3자의 팔로워 목록에 차단한 사람이 그대로 있다.
		mockMvc.perform(get("/api/v1/users/{handle}/followers", "third").header("Authorization", mine))
				.andExpect(jsonPath("$.items.length()").value(2));
		mockMvc.perform(get("/api/v1/users/{handle}", "third").header("Authorization", mine))
				.andExpect(jsonPath("$.followerCount").value(2));

		// 내 팔로워 목록에도 그대로 있다. 상대가 건 팔로우는 차단으로 끊기지 않는다.
		mockMvc.perform(get("/api/v1/users/{handle}/followers", "irene").header("Authorization", mine))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].handle").value("them"));

		// 그래도 그 사람의 프로필로는 들어갈 수 없다 — 관계는 보이고 내용은 가려진다.
		mockMvc.perform(get("/api/v1/users/{handle}", "them").header("Authorization", mine))
				.andExpect(status().isNotFound());
	}

	/**
	 * 차단하면 그 사람이 만든 알림도 보이지 않는다(plan.md Phase 10 — "차단이 들어올 때
	 * 차단한 사람의 알림은 오지 않는다를 함께 지켜야 한다").
	 *
	 * <p><b>목록과 안 읽은 수가 같은 기준이어야 한다.</b> 목록에서만 빼면 배지에 1이 떠 있는데
	 * 열면 비어 있다 — 사용자는 읽을 수 없는 알림을 영영 들고 다닌다.
	 *
	 * <p>알림 행은 지우지 않는다. 차단을 풀면 돌아온다 — 있었던 일의 기록이다.
	 */
	@Test
	@DisplayName("차단하면 그 사람이 만든 알림이 목록과 안 읽은 수에서 함께 빠진다")
	void hidesNotificationsFromBlocked() throws Exception {
		// setUp의 팔로우가 이미 하나를 만들었고, 좋아요가 하나를 더 만든다. 둘 다 같은
		// 사람이 만든 것이라 차단하면 함께 빠져야 한다.
		mockMvc.perform(post("/api/v1/posts/{id}/likes", myPostId).header("Authorization", theirs))
				.andExpect(status().isNoContent());
		awaitNotificationCount(2);

		block(mine, "them");

		mockMvc.perform(get("/api/v1/notifications").header("Authorization", mine))
				.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", mine))
				.andExpect(jsonPath("$.count").value(0));

		// 풀면 돌아온다.
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.delete("/api/v1/users/{handle}/block", "them").header("Authorization", mine))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/notifications").header("Authorization", mine))
				.andExpect(jsonPath("$.items.length()").value(2));
	}

	/** 알림은 커밋 뒤 비동기로 만들어진다. 값이 나타날 때까지 잠깐 기다린다. */
	private void awaitNotificationCount(int expected) throws Exception {
		for (int i = 0; i < 50; i++) {
			String body = mockMvc.perform(get("/api/v1/notifications/unread-count")
							.header("Authorization", mine))
					.andReturn().getResponse().getContentAsString();
			if (((Number) JsonPath.read(body, "$.count")).intValue() == expected) {
				return;
			}
			Thread.sleep(100);
		}
		mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", mine))
				.andExpect(jsonPath("$.count").value(expected));
	}

	private void block(String who, String handle) throws Exception {
		mockMvc.perform(post("/api/v1/users/{handle}/block", handle).header("Authorization", who))
				.andExpect(status().isNoContent());
	}

	private void follow(String who, String handle) throws Exception {
		mockMvc.perform(post("/api/v1/users/{handle}/follow", handle).header("Authorization", who))
				.andExpect(status().isNoContent());
	}

	private Long writePost(String who, String content) throws Exception {
		String body = mockMvc.perform(post("/api/v1/posts").header("Authorization", who)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "content": "%s"}""".formatted(bookId, content)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}
}
