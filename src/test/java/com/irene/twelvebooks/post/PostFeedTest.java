package com.irene.twelvebooks.post;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 책별 감상평과 탐색 피드. 목록의 계약은 "중복도 누락도 없이 최신순"과
 * "페이지 크기를 바꿔도 쿼리 수가 늘지 않는다" 둘이다.
 */
class PostFeedTest extends AbstractIntegrationTest {

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
	private Long bookId;
	private Long otherBookId;
	private Long meId;
	private Long otherId;
	private Long thirdId;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		meId = me.getId();
		otherId = other.getId();
		thirdId = userRepository.save(
				User.create("third@example.com", "hash", "third", "제삼자")).getId();
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
		otherBookId = bookRepository.save(Book.withIsbn13("9788966262472", "클린 코드",
				"로버트 마틴", "인사이트", null, null)).getId();
	}

	/** 목록만 보는 테스트라 HTTP를 거치지 않고 직접 넣는다 — 준비가 본론을 가리지 않게. */
	private void given(Long authorId, Long targetBookId, int count) {
		for (int i = 1; i <= count; i++) {
			postRepository.save(Post.write(authorId, targetBookId, null, "감상 " + i, null, null, false));
		}
	}

	@Test
	@DisplayName("책별 목록은 그 책의 글만 최신순으로 준다")
	void listsPostsOfOneBook() throws Exception {
		given(meId, bookId, 2);
		given(otherId, otherBookId, 3);

		mockMvc.perform(get("/api/v1/books/" + bookId + "/posts").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].content").value("감상 2"))
				.andExpect(jsonPath("$.items[0].author.handle").value("irene"))
				.andExpect(jsonPath("$.items[0].book.title").value("코드 컴플리트"))
				.andExpect(jsonPath("$.hasNext").value(false))
				.andExpect(jsonPath("$.nextCursor").doesNotExist());
	}

	/** 홈은 <b>보는 사람 본인의 글을 뺀</b> 전체다. 내 글은 {@code /users/{handle}/posts}에 있다. */
	@Test
	@DisplayName("홈은 팔로우와 무관하게 남들의 글을 최신순으로 준다")
	void listsEveryonesPosts() throws Exception {
		given(meId, bookId, 2);
		given(otherId, otherBookId, 3);

		mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(3))
				.andExpect(jsonPath("$.items[0].author.handle").value("other"));
	}

	@Test
	@DisplayName("커서로 두 번째 페이지를 받으면 중복도 누락도 없다")
	void pagesWithoutOverlapOrGap() throws Exception {
		given(otherId, bookId, 5);

		String first = mockMvc.perform(get("/api/v1/feed")
						.param("size", "2").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();

		Long cursor = JsonPath.parse(first).read("$.nextCursor", Long.class);
		String second = mockMvc.perform(get("/api/v1/feed")
						.param("size", "2").param("cursor", String.valueOf(cursor))
						.header("Authorization", bearer))
				.andReturn().getResponse().getContentAsString();

		List<String> page1 = JsonPath.parse(first).read("$.items[*].content");
		List<String> page2 = JsonPath.parse(second).read("$.items[*].content");
		assertThat(page1).containsExactly("감상 5", "감상 4");
		assertThat(page2).containsExactly("감상 3", "감상 2");
	}

	@Test
	@DisplayName("size는 50을 넘지 못하고, 0 이하는 기본값으로 되돌린다")
	void clampsSize() throws Exception {
		given(otherId, bookId, 51);

		mockMvc.perform(get("/api/v1/feed").param("size", "9999")
						.header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(50));

		mockMvc.perform(get("/api/v1/feed").param("size", "0")
						.header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(20));
	}

	@Test
	@DisplayName("페이지가 커져도 쿼리 수는 그대로다")
	void doesNotGrowQueriesWithPageSize() throws Exception {
		given(otherId, bookId, 3);
		given(thirdId, otherBookId, 27);

		long small = queriesFor(5);
		long large = queriesFor(30);

		assertThat(large).isEqualTo(small);
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
	@DisplayName("없는 책의 목록은 빈 목록이 아니라 404다")
	void rejectsUnknownBook() throws Exception {
		mockMvc.perform(get("/api/v1/books/999999/posts").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("B001"));
	}

	@Test
	@DisplayName("글이 없으면 빈 목록이다")
	void returnsEmptyPage() throws Exception {
		mockMvc.perform(get("/api/v1/books/" + bookId + "/posts").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.hasNext").value(false));
	}
}
