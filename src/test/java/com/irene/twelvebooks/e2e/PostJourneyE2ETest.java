package com.irene.twelvebooks.e2e;

import com.irene.twelvebooks.auth.JwtProvider;
import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.reading.Reading;
import com.irene.twelvebooks.reading.ReadingRepository;
import com.irene.twelvebooks.reading.ReadingStatus;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4의 핵심 여정. 서재에 담지 않은 책에 감상을 남기는 순간 기록이 공개된 글이 되고,
 * 책 페이지와 홈에 함께 나타난다.
 *
 * <p>여기서 처음으로 두 사람이 같은 화면에서 만난다 — 그 전까지는 혼자 쓰는 기록 앱이었다.
 */
class PostJourneyE2ETest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ReadingRepository readingRepository;

	@Autowired
	BookRepository bookRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String bearer;
	private String otherBearer;
	private Long myId;
	private Long bookId;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		myId = me.getId();
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", "https://example.com/c.jpg", null)).getId();
	}

	@Test
	@DisplayName("서재에 담지 않은 책에 감상을 남기면 서재가 생기고, 책 페이지와 홈에 걸린다")
	void writeWithoutShelving() throws Exception {
		// 1. 아직 서재가 비어 있다
		mockMvc.perform(get("/api/v1/users/irene/library").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0));

		// 2. 담지 않은 채로 지하철에서 읽은 만큼 쓴다
		String written = mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"content":"47~92쪽. 화자가 갑자기 믿을 수 없어진다.",
								 "fromPage":47,"toPage":92}""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fromPage").value(47))
				.andExpect(jsonPath("$.toPage").value(92))
				.andReturn().getResponse().getContentAsString();
		Long postId = JsonPath.parse(written).read("$.id", Long.class);

		// 3. 서재가 저절로 생겼다 — 읽는 중으로
		Reading reading = readingRepository.findByUserIdAndBookId(myId, bookId).orElseThrow();
		assertThat(reading.getStatus()).isEqualTo(ReadingStatus.READING);
		mockMvc.perform(get("/api/v1/users/irene/library").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].book.title").value("코드 컴플리트"));

		// 4. 같은 책을 읽는 남이 그 글을 책 페이지에서 본다
		mockMvc.perform(get("/api/v1/books/" + bookId + "/posts").header("Authorization", otherBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].author.displayName").value("아이린"))
				.andExpect(jsonPath("$.items[0].book.thumbnailUrl").isNotEmpty());

		// 5. 남도 같은 책에 쓴다 — 그 사람의 서재도 함께 생긴다
		mockMvc.perform(post("/api/v1/posts").header("Authorization", otherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"content":"나도 거기서 멈췄다","spoiler":true}""".formatted(bookId)))
				.andExpect(status().isCreated());
		assertThat(readingRepository.count()).isEqualTo(2);

		// 6. 홈은 팔로우 없이도 남의 글을 보여준다. 내 글은 홈이 아니라 내 글 목록에 있다.
		String feed = mockMvc.perform(get("/api/v1/feed").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		List<String> handles = JsonPath.parse(feed).read("$.items[*].author.handle");
		assertThat(handles).containsExactly("other");

		mockMvc.perform(get("/api/v1/users/irene/posts").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].author.handle").value("irene"));

		// 7. 남의 글은 지울 수 없다
		mockMvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", otherBearer))
				.andExpect(status().isForbidden());

		// 8. 내 글을 지워도 서재 기록은 남는다 — 서재에서 뺀 것이 아니다
		mockMvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", bearer))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/users/irene/posts").header("Authorization", bearer))
				.andExpect(jsonPath("$.items.length()").value(0));
		assertThat(readingRepository.findByUserIdAndBookId(myId, bookId)).isPresent();
	}

	@Test
	@DisplayName("서재에서 책을 빼도 그때 쓴 글은 남는다")
	void keepsPostsWhenReadingRemoved() throws Exception {
		String written = mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"content":"여기까지 읽었다"}""".formatted(bookId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long postId = JsonPath.parse(written).read("$.id", Long.class);
		Long readingId = JsonPath.parse(written).read("$.readingId", Long.class);

		mockMvc.perform(delete("/api/v1/readings/" + readingId).header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("여기까지 읽었다"))
				.andExpect(jsonPath("$.readingId").doesNotExist());
	}
}
