package com.irene.twelvebooks.follow;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.post.Post;
import com.irene.twelvebooks.post.PostRepository;
import com.irene.twelvebooks.support.AbstractIntegrationTest;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
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

/**
 * 팔로잉 타임라인. 탐색 피드와 달리 <b>내가 고른 사람들</b>의 글만 흐른다.
 */
class TimelineTest extends AbstractIntegrationTest {

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

	@Autowired
	SessionFactory sessionFactory;

	private String bearer;
	private Long meId;
	private Long friendId;
	private Long strangerId;
	private Long bookId;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User friend = userRepository.save(User.create("friend@example.com", "hash", "friend", "친구"));
		User stranger = userRepository.save(User.create("stranger@example.com", "hash", "stranger", "남"));
		meId = me.getId();
		friendId = friend.getId();
		strangerId = stranger.getId();
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
	}

	private void write(Long authorId, String content) {
		postRepository.save(Post.write(authorId, bookId, null, content, null, null, false));
	}

	private void follow(String handle) throws Exception {
		mockMvc.perform(post("/api/v1/users/" + handle + "/follow").header("Authorization", bearer))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("팔로우한 사람과 내 글만 흐르고 남의 글은 섞이지 않는다")
	void showsFollowingsAndSelfOnly() throws Exception {
		follow("friend");
		write(strangerId, "남의 글");
		write(friendId, "친구의 글");
		write(meId, "내 글");

		String feed = mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andReturn().getResponse().getContentAsString();

		List<String> contents = JsonPath.parse(feed).read("$.items[*].content");
		assertThat(contents).containsExactly("내 글", "친구의 글");
	}

	@Test
	@DisplayName("아무도 팔로우하지 않아도 내 글은 보인다")
	void alwaysIncludesMyOwnPosts() throws Exception {
		write(strangerId, "남의 글");
		write(meId, "내 글");

		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].content").value("내 글"));
	}

	@Test
	@DisplayName("언팔로우하면 그 사람의 글이 타임라인에서 사라진다")
	void dropsPostsAfterUnfollow() throws Exception {
		follow("friend");
		write(friendId, "친구의 글");

		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1));

		mockMvc.perform(delete("/api/v1/users/friend/follow").header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	@DisplayName("커서로 두 번째 페이지를 받으면 중복도 누락도 없다")
	void pagesWithoutOverlapOrGap() throws Exception {
		follow("friend");
		for (int i = 1; i <= 5; i++) {
			write(friendId, "글 " + i);
		}

		String first = mockMvc.perform(get("/api/v1/feed").param("size", "2")
						.header("Authorization", bearer))
				.andExpect(jsonPath("$.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();
		Long cursor = JsonPath.parse(first).read("$.nextCursor", Long.class);

		String second = mockMvc.perform(get("/api/v1/feed").param("size", "2")
						.param("cursor", String.valueOf(cursor)).header("Authorization", bearer))
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.parse(first).<List<String>>read("$.items[*].content"))
				.containsExactly("글 5", "글 4");
		assertThat(JsonPath.parse(second).<List<String>>read("$.items[*].content"))
				.containsExactly("글 3", "글 2");
	}

	@Test
	@DisplayName("작성자·책이 함께 실리고, 페이지가 커져도 쿼리 수는 그대로다")
	void carriesAuthorAndDoesNotGrowQueries() throws Exception {
		follow("friend");
		for (int i = 1; i <= 30; i++) {
			write(friendId, "글 " + i);
		}

		mockMvc.perform(get("/api/v1/feed").param("size", "5").header("Authorization", bearer))
				.andExpect(jsonPath("$.items[0].author.handle").value("friend"))
				.andExpect(jsonPath("$.items[0].book.title").value("코드 컴플리트"));

		assertThat(queriesFor(30)).isEqualTo(queriesFor(5));
	}

	private long queriesFor(int size) throws Exception {
		Statistics statistics = sessionFactory.getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		mockMvc.perform(get("/api/v1/feed").param("size", String.valueOf(size))
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(size));

		return statistics.getPrepareStatementCount();
	}

	@Test
	@DisplayName("토큰 없이는 타임라인을 볼 수 없다")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/feed"))
				.andExpect(status().isUnauthorized());
	}
}
