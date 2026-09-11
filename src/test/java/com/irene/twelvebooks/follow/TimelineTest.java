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
 * 홈({@code /feed})과 팔로잉 전용({@code /feed/following}).
 *
 * <p>홈은 인스타·트위터처럼 <b>팔로우한 사람과 안 한 사람의 글이 섞여</b> 흐르고, 각 항목에
 * 팔로잉 여부가 붙는다. 서버가 섞어 주는 이유는 취향이 아니라 기술이다 — 클라이언트가 두 목록을
 * 이어 붙이면 팔로우한 사람의 글이 양쪽에 다 나와 <b>중복</b>되고, 커서도 둘을 따로 굴려야 한다.
 *
 * <p>내 글은 어느 쪽에도 없다. 그건 {@code /users/{handle}/posts}가 준다.
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
	 * 타임라인은 <b>내가 고른 사람들의 글만</b> 흐른다. 내 글은 섞이지 않는다.
	 *
	 * <p>Phase 5에서는 본인 글을 포함했다 — "자기 글이 안 보이는 타임라인은 어색하다"는 이유였다.
	 * 화면을 만들어 보니 반대였다. 홈에 내 글과 남의 글이 섞이면 무엇을 보는 화면인지 흐려진다.
	 * 인스타·트위터가 홈에 자기 글을 섞지 않는 것과 같다. 내 글은 {@code /users/{handle}/posts}로
	 * 따로 본다.
	 */
	@Test
	@DisplayName("홈은 팔로우한 사람과 안 한 사람의 글이 섞여 흐르고, 각각 팔로잉 여부가 붙는다")
	void homeMixesFollowedAndUnfollowed() throws Exception {
		follow("friend");
		write(strangerId, "남의 글");
		write(friendId, "친구의 글");
		write(meId, "내 글");

		String feed = mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(status().isOk())
				// 내 글만 빠지고 나머지는 최신순으로 섞인다
				.andExpect(jsonPath("$.items.length()").value(2))
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.parse(feed).<List<String>>read("$.items[*].content"))
				.containsExactly("친구의 글", "남의 글");
		assertThat(JsonPath.parse(feed)
				.<List<Boolean>>read("$.items[*].followingAuthor"))
				.containsExactly(true, false);
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

	/**
	 * 아무도 팔로우하지 않아도 홈은 비지 않는다 — 그게 홈을 섞는 이유다.
	 * 팔로잉 전용 목록만 빈다.
	 */
	@Test
	@DisplayName("팔로우가 0명이어도 홈에는 글이 흐른다")
	void homeIsNotEmptyWithoutFollowings() throws Exception {
		write(strangerId, "남의 글");
		write(meId, "내 글");

		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].content").value("남의 글"))
				.andExpect(jsonPath("$.items[0].followingAuthor").value(false));

		mockMvc.perform(get("/api/v1/feed/following").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	@DisplayName("언팔로우하면 팔로잉 목록에서 빠지고, 홈에는 남되 표시가 바뀐다")
	void dropsPostsAfterUnfollow() throws Exception {
		follow("friend");
		write(friendId, "친구의 글");

		mockMvc.perform(get("/api/v1/feed/following").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1));

		mockMvc.perform(delete("/api/v1/users/friend/follow").header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/feed/following").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		// 글이 사라지는 게 아니라 관계 표시만 바뀐다
		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].followingAuthor").value(false));
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

		mockMvc.perform(get("/api/v1/feed").param("size", "5").header("Authorization", bearer))
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
