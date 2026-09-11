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
 * 홈({@code /feed})과 팔로잉 전용({@code /feed/following}). 어느 쪽에도 내 글은 없다 —
 * 그건 {@code /users/{handle}/posts}가 준다.
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
	FollowRepository followRepository;

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

	/**
	 * 홈과 팔로잉 전용은 <b>서로 겹치지 않는다.</b> 홈에서 팔로잉을 빼기 때문에, 화면이 둘을
	 * 이어 붙여도 같은 글이 두 번 나오지 않는다 — 섞는 비율은 화면이 정한다.
	 */
	@Test
	@DisplayName("홈에는 내 글도 팔로우한 사람의 글도 없다")
	void homeExcludesMineAndFollowings() throws Exception {
		follow("friend");
		write(strangerId, "남의 글");
		write(friendId, "친구의 글");
		write(meId, "내 글");

		String feed = mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.parse(feed).<List<String>>read("$.items[*].content"))
				.containsExactly("남의 글");
	}

	@Test
	@DisplayName("팔로우하면 그 사람 글이 홈에서 빠져 팔로잉 목록으로 옮겨 간다")
	void followingMovesPostsFromHomeToFollowing() throws Exception {
		write(friendId, "친구의 글");

		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1));
		mockMvc.perform(get("/api/v1/feed/following").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));

		follow("friend");

		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/feed/following").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	@DisplayName("팔로잉 전용 목록은 내가 고른 사람들의 글만 준다")
	void followingOnlyList() throws Exception {
		follow("friend");
		write(strangerId, "남의 글");
		write(friendId, "친구의 글");
		write(meId, "내 글");

		String feed = mockMvc.perform(get("/api/v1/feed/following").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.parse(feed).<List<String>>read("$.items[*].content"))
				.containsExactly("친구의 글");
	}

	/** 아무도 팔로우하지 않아도 홈은 비지 않는다. 팔로잉 전용 목록만 빈다. */
	@Test
	@DisplayName("팔로우가 0명이어도 홈에는 글이 흐른다")
	void homeIsNotEmptyWithoutFollowings() throws Exception {
		write(strangerId, "남의 글");
		write(meId, "내 글");

		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].content").value("남의 글"));

		mockMvc.perform(get("/api/v1/feed/following").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	@DisplayName("언팔로우하면 팔로잉 목록에서 빠지고 홈으로 돌아온다")
	void dropsPostsAfterUnfollow() throws Exception {
		follow("friend");
		write(friendId, "친구의 글");

		mockMvc.perform(get("/api/v1/feed/following").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1));
		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));

		mockMvc.perform(delete("/api/v1/users/friend/follow").header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/feed/following").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	@DisplayName("커서로 두 번째 페이지를 받으면 중복도 누락도 없다")
	void pagesWithoutOverlapOrGap() throws Exception {
		follow("friend");
		for (int i = 1; i <= 5; i++) {
			write(friendId, "글 " + i);
		}

		String first = mockMvc.perform(get("/api/v1/feed/following").param("size", "2")
						.header("Authorization", bearer))
				.andExpect(jsonPath("$.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();
		Long cursor = JsonPath.parse(first).read("$.nextCursor", Long.class);

		String second = mockMvc.perform(get("/api/v1/feed/following").param("size", "2")
						.param("cursor", String.valueOf(cursor)).header("Authorization", bearer))
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.parse(first).<List<String>>read("$.items[*].content"))
				.containsExactly("글 5", "글 4");
		assertThat(JsonPath.parse(second).<List<String>>read("$.items[*].content"))
				.containsExactly("글 3", "글 2");
	}

	@Test
	@DisplayName("작성자·책이 함께 실린다")
	void carriesAuthorAndBook() throws Exception {
		follow("friend");
		write(friendId, "친구의 글");

		mockMvc.perform(get("/api/v1/feed/following").param("size", "5").header("Authorization", bearer))
				.andExpect(jsonPath("$.items[0].author.handle").value("friend"))
				.andExpect(jsonPath("$.items[0].book.title").value("코드 컴플리트"));
	}

	/**
	 * 글마다 <b>작성자와 책이 전부 다르다.</b> 한 사람이 한 책에 30번 쓴 것으로 채우면,
	 * 구현이 글마다 따로 조회하도록 바뀌어도 영속성 컨텍스트의 1차 캐시가 두 번째 조회부터
	 * 흡수해 버려 쿼리 수가 늘지 않는다 — 회귀를 놓치는 테스트가 된다.
	 */
	@Test
	@DisplayName("페이지가 커져도 쿼리 수는 그대로다")
	void doesNotGrowQueriesWithPageSize() throws Exception {
		for (int i = 1; i <= 30; i++) {
			User author = userRepository.save(
					User.create("a%d@example.com".formatted(i), "hash", "author%d".formatted(i), "저자" + i));
			Long otherBookId = bookRepository.save(Book.withSourceKey("key-%d".formatted(i),
					"책 " + i, "지은이 " + i, "출판사", null, null)).getId();
			followRepository.save(Follow.of(meId, author.getId()));
			postRepository.save(Post.write(author.getId(), otherBookId, null, "글 " + i, null, null, false));
		}

		assertThat(queriesFor(30)).isEqualTo(queriesFor(5));
	}

	private long queriesFor(int size) throws Exception {
		Statistics statistics = sessionFactory.getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		mockMvc.perform(get("/api/v1/feed/following").param("size", String.valueOf(size))
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(size));

		return statistics.getPrepareStatementCount();
	}

	@Test
	@DisplayName("토큰 없이는 볼 수 없다")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/feed")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/feed/following")).andExpect(status().isUnauthorized());
	}
}
