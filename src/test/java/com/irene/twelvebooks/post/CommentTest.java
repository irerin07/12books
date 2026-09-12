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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 댓글. 1단계뿐이라 대댓글이 없고, 읽은 분량도 붙지 않는다 — 감상평만 "어디까지 읽고 쓴
 * 글"이고 댓글은 그 글에 대한 말이다.
 */
class CommentTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	/** 글쓴이 */
	private String authorBearer;

	/** 댓글을 다는 남 */
	private String otherBearer;

	/** 아무 관계도 없는 제삼자 */
	private String thirdBearer;

	private Long postId;

	@BeforeEach
	void setUp() throws Exception {
		User author = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		User third = userRepository.save(User.create("third@example.com", "hash", "third", "제삼자"));
		authorBearer = "Bearer " + jwtProvider.createAccessToken(author.getId(), author.getHandle());
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());
		thirdBearer = "Bearer " + jwtProvider.createAccessToken(third.getId(), third.getHandle());

		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();

		String body = mockMvc.perform(post("/api/v1/posts").header("Authorization", authorBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "content": "47~92쪽. 이름 짓기에 이렇게까지 지면을 쓴다."}
								""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		postId = ((Number) JsonPath.parse(body).read("$.id")).longValue();
	}

	private long comment(String bearerToken, String content) throws Exception {
		String body = mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
						.header("Authorization", bearerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"%s\"}".formatted(content)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.parse(body).read("$.id")).longValue();
	}

	@Test
	@DisplayName("댓글을 달면 글의 commentCount가 오르고 목록에 최신순으로 쌓인다")
	void writesComment() throws Exception {
		comment(otherBearer, "저도 그 장에서 멈췄어요.");
		comment(thirdBearer, "이름 짓기 장이 제일 좋았습니다.");

		mockMvc.perform(get("/api/v1/posts/" + postId + "/comments").header("Authorization", authorBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				// 다른 목록과 같이 id desc다 — 나중에 단 댓글이 위에 온다.
				.andExpect(jsonPath("$.items[0].content").value("이름 짓기 장이 제일 좋았습니다."))
				.andExpect(jsonPath("$.items[0].author.handle").value("third"))
				.andExpect(jsonPath("$.items[1].author.displayName").value("남"))
				.andExpect(jsonPath("$.hasNext").value(false));

		mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", authorBearer))
				.andExpect(jsonPath("$.commentCount").value(2));
	}

	@Test
	@DisplayName("댓글을 지우면 commentCount가 내려간다")
	void deletesComment() throws Exception {
		long commentId = comment(otherBearer, "저도 그 장에서 멈췄어요.");

		mockMvc.perform(delete("/api/v1/comments/" + commentId).header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", authorBearer))
				.andExpect(jsonPath("$.commentCount").value(0));
		mockMvc.perform(get("/api/v1/posts/" + postId + "/comments").header("Authorization", authorBearer))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	@DisplayName("글쓴이는 자기 글에 달린 남의 댓글을 지울 수 있다")
	void postAuthorCanDeleteOthersComment() throws Exception {
		long commentId = comment(otherBearer, "읽지도 않고 아는 척하네요.");

		// 내 글 아래에 무엇이 남는지는 글쓴이도 정할 수 있어야 한다. 신고·차단이 아직 없어
		// 이것이 유일한 수단이다(spec.md §3.2).
		mockMvc.perform(delete("/api/v1/comments/" + commentId).header("Authorization", authorBearer))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("남의 글에 달린 남의 댓글은 지울 수 없다")
	void thirdPartyCannotDelete() throws Exception {
		long commentId = comment(otherBearer, "저도 그 장에서 멈췄어요.");

		mockMvc.perform(delete("/api/v1/comments/" + commentId).header("Authorization", thirdBearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("A004"));

		mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", authorBearer))
				.andExpect(jsonPath("$.commentCount").value(1));
	}

	@Test
	@DisplayName("빈 댓글은 달 수 없고, 없는 글·없는 댓글은 404다")
	void rejectsInvalidInput() throws Exception {
		mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
						.header("Authorization", otherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));

		mockMvc.perform(post("/api/v1/posts/999999/comments")
						.header("Authorization", otherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"없는 글에 답니다\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("P001"));

		mockMvc.perform(delete("/api/v1/comments/999999").header("Authorization", otherBearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("P003"));
	}

	@Test
	@DisplayName("글을 지우면 달려 있던 댓글도 함께 사라진다")
	void deletingPostRemovesComments() throws Exception {
		comment(otherBearer, "저도 그 장에서 멈췄어요.");

		mockMvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", authorBearer))
				.andExpect(status().isNoContent());

		// 글이 없으니 댓글 목록도 없다. 남아 있으면 지워진 글에 매달린 고아 행이 된다.
		mockMvc.perform(get("/api/v1/posts/" + postId + "/comments").header("Authorization", authorBearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("P001"));
	}
}
