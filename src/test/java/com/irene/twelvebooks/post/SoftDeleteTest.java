package com.irene.twelvebooks.post;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 삭제는 행을 지우지 않고 플래그를 세운다.
 *
 * <p>지운 뒤에도 "무엇이 있었는지"를 물을 수 있어야 한다 — 잘못 지웠다는 신고, 남용 조사,
 * 통계의 소급 정정은 전부 지워진 뒤에 온다. 행을 지우면 그 질문에 답할 방법이 사라지고,
 * 백업에서 한 행만 되살리는 일은 실무에서 사실상 불가능하다.
 *
 * <p>그래서 이 테스트는 <b>API에서 사라졌는지</b>와 <b>행은 남아 있는지</b>를 함께 본다.
 * 앞만 보면 행 삭제도 통과하고, 뒤만 보면 지워지지 않은 글도 통과한다.
 */
class SoftDeleteTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private String bearer;
	private String otherBearer;
	private Long bookId;
	private Long postId;

	@BeforeEach
	void setUp() throws Exception {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		postId = writePost();
	}

	private Long writePost() throws Exception {
		String body = mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "content": "47~92쪽. 이름 짓기에 이렇게까지 지면을 쓴다."}
								""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.parse(body).read("$.id")).longValue();
	}

	private long rows(String table, String where) {
		return jdbcTemplate.queryForObject(
				"select count(*) from " + table + " where " + where, Long.class);
	}

	@Test
	@DisplayName("지운 감상평은 어디에서도 안 보이지만 행은 남고 지운 시각이 찍힌다")
	void softDeletesPost() throws Exception {
		mockMvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("P001"));
		mockMvc.perform(get("/api/v1/users/irene/posts").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/books/" + bookId + "/posts").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		// 남의 홈에도 뜨면 안 된다. 지운 글이 남의 화면에 남는 것이 가장 나쁜 실패다.
		mockMvc.perform(get("/api/v1/feed").header("Authorization", otherBearer))
				.andExpect(jsonPath("$.items.length()").value(0));

		assertThat(rows("posts", "id = " + postId + " and deleted_at is not null")).isEqualTo(1);
	}

	@Test
	@DisplayName("지운 감상평에는 좋아요도 댓글도 달 수 없다")
	void rejectsReactionsOnDeletedPost() throws Exception {
		mockMvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("P001"));
		mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
						.header("Authorization", otherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"지워진 글에 답니다\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("P001"));
	}

	@Test
	@DisplayName("이미 지운 감상평을 또 지우면 404다")
	void rejectsSecondDelete() throws Exception {
		mockMvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", bearer))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("P001"));
	}

	@Test
	@DisplayName("지운 댓글은 목록에서 빠지고 카운터도 줄지만 행은 남는다")
	void softDeletesComment() throws Exception {
		String body = mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
						.header("Authorization", otherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"저도 그 장에서 멈췄어요.\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long commentId = ((Number) JsonPath.parse(body).read("$.id")).longValue();

		mockMvc.perform(delete("/api/v1/comments/" + commentId).header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/posts/" + postId + "/comments").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", bearer))
				.andExpect(jsonPath("$.commentCount").value(0));

		assertThat(rows("comments", "id = " + commentId + " and deleted_at is not null")).isEqualTo(1);
	}

	@Test
	@DisplayName("지워진 글의 댓글은 지울 수 없고, 좋아요 취소는 아무것도 바꾸지 않는다")
	void doesNotTouchChildrenOfDeletedPost() throws Exception {
		String body = mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
						.header("Authorization", otherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"저도 그 장에서 멈췄어요.\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long commentId = ((Number) JsonPath.parse(body).read("$.id")).longValue();

		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", bearer))
				.andExpect(status().isNoContent());

		// 지워진 글의 댓글에는 닿을 길이 없다. 지울 수 있으면 남겨 둔 글의 commentCount만
		// 어긋난다 — 카운터를 내리는 UPDATE는 살아 있는 글에만 걸리기 때문이다.
		mockMvc.perform(delete("/api/v1/comments/" + commentId).header("Authorization", otherBearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("P003"));

		// 취소는 멱등이라 성공으로 답하되, 지워진 글의 좋아요는 건드리지 않는다.
		// 행만 지우고 카운터가 그대로면 보존해 둔 글의 숫자가 틀어진다.
		mockMvc.perform(delete("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		assertThat(rows("comments", "id = " + commentId + " and deleted_at is null")).isEqualTo(1);
		assertThat(rows("post_likes", "post_id = " + postId)).isEqualTo(1);
		assertThat(rows("posts", "id = " + postId + " and like_count = 1 and comment_count = 1"))
				.isEqualTo(1);
	}

	/**
	 * 서재의 "빼기"는 삭제가 아니라 목록에서 내리는 일이다. 그래서 컬럼 이름도 {@code
	 * deleted_at}이 아니라 {@code in_bookshelf}이고, 다시 담아도 진도가 초기화되지 않는다 —
	 * 사용자는 목록에서 내려 달라고 했지 읽은 기록을 지워 달라고 한 적이 없다.
	 */
	@Test
	@DisplayName("서재에서 빼도 기록은 남고, 다시 담으면 읽던 진도가 그대로다")
	void unshelvesAndKeepsProgressOnReshelve() throws Exception {
		// setUp의 감상평이 bookId를 이미 서재에 담았다(글을 쓰면 자동으로 담긴다). 담기부터
		// 확인하려면 손대지 않은 책이 필요하다.
		Long freshBookId = bookRepository.save(Book.withIsbn13("9788966262281", "리팩터링",
				"마틴 파울러", "한빛미디어", null, null)).getId();

		String body = mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookId\": %d, \"status\": \"READING\"}".formatted(freshBookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long readingId = ((Number) JsonPath.parse(body).read("$.id")).longValue();

		// 200쪽까지 읽어 둔다. 다시 담았을 때 이 자리가 남아 있어야 한다.
		mockMvc.perform(patch("/api/v1/readings/" + readingId).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pageCount\": 500, \"currentPage\": 200}"))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/readings/" + readingId).header("Authorization", bearer))
				.andExpect(status().isNoContent());

		// setUp의 글이 담은 책 하나만 남는다.
		mockMvc.perform(get("/api/v1/users/irene/library").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1));
		assertThat(rows("readings", "id = " + readingId + " and in_bookshelf = false")).isEqualTo(1);

		// uk(user_id, book_id)가 그대로라 다시 담기는 insert가 아니라 그 행을 다시 꽂는 일이다.
		mockMvc.perform(post("/api/v1/readings").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookId\": %d, \"status\": \"READING\"}".formatted(freshBookId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("READING"))
				// 책갈피가 그 자리에 있다. 목록에서 내렸을 뿐 읽은 것을 지운 적이 없다.
				.andExpect(jsonPath("$.currentPage").value(200))
				.andExpect(jsonPath("$.pageCount").value(500));

		// 행이 늘지 않는다 — 같은 행을 다시 꽂은 것이다(setUp의 것까지 둘).
		assertThat(rows("readings", "user_id is not null")).isEqualTo(2);
		mockMvc.perform(get("/api/v1/users/irene/library").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(2));
	}
}
