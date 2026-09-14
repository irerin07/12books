package com.irene.twelvebooks.notification;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 반응이 본인에게 닿는 자리.
 *
 * <p>알림은 <b>커밋 이후</b>에 만들어진다. 좋아요 트랜잭션 안에서 만들면 알림 저장이 실패했을
 * 때 좋아요까지 롤백되는데, 부가 기능이 본 기능을 되돌리면 안 되기 때문이다. 그래서 요청이
 * 200을 돌려준 뒤에도 알림은 아직 없을 수 있다 — 이 테스트가 기다리는 이유다.
 */
class NotificationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	/** 글쓴이 — 알림을 받는 쪽 */
	private String authorBearer;

	/** 반응하는 남 */
	private String otherBearer;

	private Long postId;

	@BeforeEach
	void setUp() throws Exception {
		User author = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		authorBearer = "Bearer " + jwtProvider.createAccessToken(author.getId(), author.getHandle());
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());

		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		String body = mockMvc.perform(post("/api/v1/posts").header("Authorization", authorBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "content": "47~92쪽. 이름 짓기에 지면을 많이 쓴다."}
								""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		postId = ((Number) JsonPath.parse(body).read("$.id")).longValue();
	}

	/**
	 * 알림은 커밋 뒤에 만들어지므로 요청이 돌아온 시점에는 아직 없을 수 있다.
	 *
	 * <p>고정 시간을 자면 느린 기계에서 깨지고 빠른 기계에서 느려진다. 조건이 맞을 때까지
	 * 짧게 여러 번 본다.
	 */
	private void awaitUnreadCount(String bearer, int expected) throws Exception {
		for (int attempt = 0; attempt < 50; attempt++) {
			String body = mockMvc.perform(
							get("/api/v1/notifications/unread-count").header("Authorization", bearer))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			if (((Number) JsonPath.parse(body).read("$.count")).intValue() == expected) {
				return;
			}
			Thread.sleep(100);
		}
		mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer))
				.andExpect(jsonPath("$.count").value(expected));
	}

	@Test
	@DisplayName("남이 좋아요를 누르면 알림이 오고, 행위자와 글이 함께 실린다")
	void notifiesOnLike() throws Exception {
		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		awaitUnreadCount(authorBearer, 1);

		mockMvc.perform(get("/api/v1/notifications").header("Authorization", authorBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].type").value("POST_LIKED"))
				.andExpect(jsonPath("$.items[0].actor.handle").value("other"))
				.andExpect(jsonPath("$.items[0].actor.displayName").value("남"))
				.andExpect(jsonPath("$.items[0].post.id").value(postId))
				.andExpect(jsonPath("$.items[0].read").value(false));
	}

	@Test
	@DisplayName("남이 댓글을 달면 알림이 오고, 팔로우도 알림이 온다")
	void notifiesOnCommentAndFollow() throws Exception {
		mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
						.header("Authorization", otherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"저도 그 장에서 멈췄어요.\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/users/irene/follow").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		awaitUnreadCount(authorBearer, 2);

		mockMvc.perform(get("/api/v1/notifications").header("Authorization", authorBearer))
				.andExpect(jsonPath("$.items[0].type").value("FOLLOWED"))
				// 팔로우에는 딸린 글이 없다. 계산하지 않은 값을 싣지 않는다.
				.andExpect(jsonPath("$.items[0].post").doesNotHaveJsonPath())
				.andExpect(jsonPath("$.items[1].type").value("POST_COMMENTED"))
				.andExpect(jsonPath("$.items[1].post.id").value(postId));
	}

	@Test
	@DisplayName("내가 한 행동은 나에게 알리지 않는다")
	void doesNotNotifySelf() throws Exception {
		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", authorBearer))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
						.header("Authorization", authorBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"덧붙이자면\"}"))
				.andExpect(status().isCreated());

		// 남이 누른 것 하나만 남아야 확인이 된다 — 0이면 "아직 안 만들어진 것"과 구분되지 않는다.
		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());
		awaitUnreadCount(authorBearer, 1);

		mockMvc.perform(get("/api/v1/notifications").header("Authorization", authorBearer))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].actor.handle").value("other"));
	}

	@Test
	@DisplayName("같은 사람이 껐다 켜도 알림은 한 건이고, 취소해도 남는다")
	void keepsOneNotificationPerEvent() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer));
			mockMvc.perform(delete("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer));
		}

		awaitUnreadCount(authorBearer, 1);
		// 취소로 끝났는데도 알림은 남는다. 알림은 있었던 일의 기록이고 취소가 그것을 없애지 않는다.
		mockMvc.perform(get("/api/v1/notifications").header("Authorization", authorBearer))
				.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	@DisplayName("읽음 처리하면 안 읽은 수가 줄고, 전체 읽음도 된다")
	void marksRead() throws Exception {
		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer));
		mockMvc.perform(post("/api/v1/users/irene/follow").header("Authorization", otherBearer));
		awaitUnreadCount(authorBearer, 2);

		String body = mockMvc.perform(get("/api/v1/notifications").header("Authorization", authorBearer))
				.andReturn().getResponse().getContentAsString();
		long first = ((Number) JsonPath.parse(body).read("$.items[0].id")).longValue();

		mockMvc.perform(patch("/api/v1/notifications/" + first + "/read")
						.header("Authorization", authorBearer))
				.andExpect(status().isNoContent());
		awaitUnreadCount(authorBearer, 1);

		mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", authorBearer))
				.andExpect(status().isNoContent());
		awaitUnreadCount(authorBearer, 0);

		// 읽어도 목록에서 사라지지는 않는다.
		mockMvc.perform(get("/api/v1/notifications").header("Authorization", authorBearer))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].read").value(true));
	}

	@Test
	@DisplayName("남의 알림은 읽음 처리할 수 없다")
	void cannotReadSomeoneElsesNotification() throws Exception {
		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer));
		awaitUnreadCount(authorBearer, 1);

		String body = mockMvc.perform(get("/api/v1/notifications").header("Authorization", authorBearer))
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.parse(body).read("$.items[0].id")).longValue();

		mockMvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", otherBearer))
				.andExpect(status().isNotFound());
	}
}
