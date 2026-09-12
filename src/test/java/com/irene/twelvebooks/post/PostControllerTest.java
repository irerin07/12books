package com.irene.twelvebooks.post;

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

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostControllerTest extends AbstractIntegrationTest {

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

	@Autowired
	Clock clock;

	private Long myId;
	private String bearer;
	private String otherBearer;
	private Long bookId;

	@BeforeEach
	void setUp() {
		User me = userRepository.save(User.create("me@example.com", "hash", "irene", "아이린"));
		User other = userRepository.save(User.create("other@example.com", "hash", "other", "남"));
		myId = me.getId();
		bearer = "Bearer " + jwtProvider.createAccessToken(me.getId(), me.getHandle());
		otherBearer = "Bearer " + jwtProvider.createAccessToken(other.getId(), other.getHandle());

		bookId = bookRepository.save(Book.withIsbn13("9788960777330", "코드 컴플리트",
				"스티브 맥코넬", "위키북스", null, null)).getId();
	}

	private String write(String bearerToken, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/posts").header("Authorization", bearerToken)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	@DisplayName("서재에 없는 책으로 써도 글이 써지고, reading이 READING으로 자동 생성돼 연결된다")
	void createsReadingWhenMissing() throws Exception {
		String body = write(bearer, """
				{"bookId":%d,"content":"47~92쪽. 화자가 갑자기 믿을 수 없어진다.","fromPage":47,"toPage":92}"""
				.formatted(bookId));

		Reading reading = readingRepository.findLiveByUserIdAndBookId(myId, bookId).orElseThrow();
		assertThat(reading.getStatus()).isEqualTo(ReadingStatus.READING);
		assertThat(reading.getStartedAt()).isNotNull();
		assertThat(JsonPath.parse(body).read("$.readingId", Long.class)).isEqualTo(reading.getId());
		assertThat(JsonPath.parse(body).read("$.content", String.class))
				.isEqualTo("47~92쪽. 화자가 갑자기 믿을 수 없어진다.");
	}

	@Test
	@DisplayName("이미 서재에 있으면 그 기록에 붙고 상태를 건드리지 않는다")
	void reusesExistingReading() throws Exception {
		Reading existing = readingRepository.save(
				Reading.of(myId, bookId, ReadingStatus.PAUSED, LocalDateTime.now(clock)));

		String body = write(bearer, """
				{"bookId":%d,"content":"다시 폈다"}""".formatted(bookId));

		assertThat(JsonPath.parse(body).read("$.readingId", Long.class)).isEqualTo(existing.getId());
		assertThat(readingRepository.count()).isEqualTo(1);
		assertThat(readingRepository.findById(existing.getId()).orElseThrow().getStatus())
				.isEqualTo(ReadingStatus.PAUSED);
	}

	@Test
	@DisplayName("응답에는 작성자와 책이 함께 붙는다")
	void carriesAuthorAndBook() throws Exception {
		String body = write(bearer, """
				{"bookId":%d,"content":"좋다","spoiler":true}""".formatted(bookId));

		assertThat(JsonPath.parse(body).read("$.author.handle", String.class)).isEqualTo("irene");
		assertThat(JsonPath.parse(body).read("$.author.displayName", String.class)).isEqualTo("아이린");
		assertThat(JsonPath.parse(body).read("$.book.title", String.class)).isEqualTo("코드 컴플리트");
		assertThat(JsonPath.parse(body).read("$.spoiler", Boolean.class)).isTrue();
		assertThat(JsonPath.parse(body).read("$.likeCount", Integer.class)).isZero();
		assertThat(JsonPath.parse(body).read("$.commentCount", Integer.class)).isZero();
	}

	@Test
	@DisplayName("단건으로 다시 꺼내 볼 수 있다")
	void readsOne() throws Exception {
		Long id = JsonPath.parse(write(bearer, """
				{"bookId":%d,"content":"좋다"}""".formatted(bookId))).read("$.id", Long.class);

		mockMvc.perform(get("/api/v1/posts/" + id).header("Authorization", otherBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.author.handle").value("irene"));
	}

	@Test
	@DisplayName("없는 글은 404")
	void returnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/posts/999999").header("Authorization", bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("P001"));
	}

	@Test
	@DisplayName("없는 책으로는 쓸 수 없다")
	void rejectsUnknownBook() throws Exception {
		mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":999999,"content":"좋다"}"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("B001"));
	}

	@Test
	@DisplayName("뒤집힌 구간과 빈 본문은 400")
	void rejectsInvalidInput() throws Exception {
		mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"content":"좋다","fromPage":92,"toPage":47}""".formatted(bookId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));

		mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"content":"   "}""".formatted(bookId)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("남의 글은 지울 수 없다")
	void protectsOtherPeoplesPosts() throws Exception {
		Long id = JsonPath.parse(write(bearer, """
				{"bookId":%d,"content":"좋다"}""".formatted(bookId))).read("$.id", Long.class);

		mockMvc.perform(delete("/api/v1/posts/" + id).header("Authorization", otherBearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("A004"));

		mockMvc.perform(get("/api/v1/posts/" + id).header("Authorization", bearer))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("내 글을 지우면 사라지지만 서재 기록은 남는다")
	void deletesOwnPost() throws Exception {
		Long id = JsonPath.parse(write(bearer, """
				{"bookId":%d,"content":"좋다"}""".formatted(bookId))).read("$.id", Long.class);

		mockMvc.perform(delete("/api/v1/posts/" + id).header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/posts/" + id).header("Authorization", bearer))
				.andExpect(status().isNotFound());
		assertThat(readingRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("토큰 없이는 쓸 수 없다")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/posts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookId":%d,"content":"좋다"}""".formatted(bookId)))
				.andExpect(status().isUnauthorized());
	}
}
