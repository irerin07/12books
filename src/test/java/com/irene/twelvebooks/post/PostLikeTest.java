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
 * 좋아요. 누른 사실은 {@code post_likes} 한 행이고, 화면이 읽는 것은 글에 붙은 카운터와
 * {@code likedByMe}다.
 */
class PostLikeTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;
	private String otherBearer;
	private Long postId;

	@BeforeEach
	void setUp() throws Exception {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());

		Long bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();

		String body = mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId": %d, "content": "47~92쪽. 이름 짓기에 이렇게까지 지면을 쓴다."}
								""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		postId = ((Number) JsonPath.parse(body).read("$.id")).longValue();
	}

	@Test
	@DisplayName("좋아요를 누르면 카운터가 오르고 누른 사람에게만 likedByMe가 참이다")
	void likes() throws Exception {
		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", otherBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.likeCount").value(1))
				.andExpect(jsonPath("$.likedByMe").value(true));

		// 같은 글인데 보는 사람이 다르면 likedByMe가 다르다. 카운터는 누가 보든 같다.
		mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", bearer))
				.andExpect(jsonPath("$.likeCount").value(1))
				.andExpect(jsonPath("$.likedByMe").value(false));
	}

	@Test
	@DisplayName("같은 글에 두 번 좋아요를 누를 수 없다")
	void rejectsDuplicateLike() throws Exception {
		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("P002"));

		// 실패한 요청이 카운터를 올려 두면 안 된다.
		mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", otherBearer))
				.andExpect(jsonPath("$.likeCount").value(1));
	}

	@Test
	@DisplayName("좋아요를 취소하면 카운터가 내려가고, 누른 적 없이 취소해도 실패하지 않는다")
	void unlikes() throws Exception {
		mockMvc.perform(post("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());
		// 두 번 눌러도 성공이다. 요청의 목적("이 글에 좋아요를 누르지 않은 상태")이 이미 이뤄졌다.
		mockMvc.perform(delete("/api/v1/posts/" + postId + "/likes").header("Authorization", otherBearer))
				.andExpect(status().isNoContent());

		// 두 번째 취소가 카운터를 한 번 더 내리면 음수가 된다. 지운 행이 없으면 건드리지 않는다.
		mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", otherBearer))
				.andExpect(jsonPath("$.likeCount").value(0))
				.andExpect(jsonPath("$.likedByMe").value(false));
	}

	@Test
	@DisplayName("없는 글에는 좋아요를 누를 수 없다")
	void rejectsLikeOnMissingPost() throws Exception {
		mockMvc.perform(post("/api/v1/posts/999999/likes").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("P001"));
	}
}
