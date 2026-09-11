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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 한 사람이 쓴 감상평 목록.
 *
 * <p>지금까지 이 목록이 없어서 <b>남의 프로필에 들어가도 그 사람 글을 볼 수 없었다.</b>
 * 책별 목록이나 피드에서 우연히 마주쳐야 했다. "내 글만 보기"도 같은 구멍의 특수한 경우다 —
 * {@code /posts/me}를 따로 두는 대신 내 handle로 이 목록을 부르면 된다.
 */
class UserPostsTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	PostRepository postRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;
	private Long meId;
	private Long otherId;
	private Long bookId;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		meId = me.getId();
		otherId = other.getId();
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
	}

	private void write(Long authorId, String content) {
		postRepository.save(Post.write(authorId, bookId, null, content, null, null, false));
	}

	@Test
	@DisplayName("그 사람이 쓴 글만 최신순으로 준다")
	void listsOnlyThatPersonsPosts() throws Exception {
		write(otherId, "남의 글");
		write(meId, "내 첫 글");
		write(meId, "내 둘째 글");

		String body = mockMvc.perform(get("/api/v1/users/irene/posts").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].author.handle").value("irene"))
				.andExpect(jsonPath("$.items[0].book.title").value("코드 컴플리트"))
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.parse(body).<List<String>>read("$.items[*].content"))
				.containsExactly("내 둘째 글", "내 첫 글");
	}

	@Test
	@DisplayName("남의 프로필에서도 그 사람 글을 본다")
	void listsSomeoneElsesPosts() throws Exception {
		write(otherId, "남의 글");
		write(meId, "내 글");

		mockMvc.perform(get("/api/v1/users/other/posts").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].content").value("남의 글"));
	}

	@Test
	@DisplayName("커서로 두 번째 페이지를 받으면 중복도 누락도 없다")
	void pagesWithoutOverlapOrGap() throws Exception {
		for (int i = 1; i <= 5; i++) {
			write(meId, "글 " + i);
		}

		String first = mockMvc.perform(get("/api/v1/users/irene/posts").param("size", "2")
						.header("Authorization", bearer))
				.andExpect(jsonPath("$.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();
		Long cursor = JsonPath.parse(first).read("$.nextCursor", Long.class);

		String second = mockMvc.perform(get("/api/v1/users/irene/posts").param("size", "2")
						.param("cursor", String.valueOf(cursor)).header("Authorization", bearer))
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.parse(first).<List<String>>read("$.items[*].content"))
				.containsExactly("글 5", "글 4");
		assertThat(JsonPath.parse(second).<List<String>>read("$.items[*].content"))
				.containsExactly("글 3", "글 2");
	}

	@Test
	@DisplayName("글이 없으면 빈 목록이다")
	void returnsEmptyPage() throws Exception {
		mockMvc.perform(get("/api/v1/users/other/posts").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("없는 사람의 목록은 404")
	void rejectsUnknownUser() throws Exception {
		mockMvc.perform(get("/api/v1/users/nobody/posts").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("U003"));
	}
}
